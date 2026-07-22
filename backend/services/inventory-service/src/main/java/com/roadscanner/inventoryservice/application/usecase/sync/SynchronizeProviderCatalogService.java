package com.roadscanner.inventoryservice.application.usecase.sync;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.FareAmount;
import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.Route;
import com.roadscanner.inventoryservice.domain.model.RouteId;
import com.roadscanner.inventoryservice.domain.model.Seat;
import com.roadscanner.inventoryservice.domain.model.SeatLayout;
import com.roadscanner.inventoryservice.domain.model.SeatNumber;
import com.roadscanner.inventoryservice.domain.model.SyncRecord;
import com.roadscanner.inventoryservice.domain.model.SyncRecordId;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.model.TripSchedule;
import com.roadscanner.inventoryservice.domain.port.in.SynchronizeProviderCatalog;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;
import com.roadscanner.inventoryservice.domain.port.out.CityRepository;
import com.roadscanner.inventoryservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.inventoryservice.domain.port.out.ProviderMappingRepository;
import com.roadscanner.inventoryservice.domain.port.out.RouteRepository;
import com.roadscanner.inventoryservice.domain.port.out.SeatLayoutRepository;
import com.roadscanner.inventoryservice.domain.port.out.SyncRecordRepository;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * Implements {@link SynchronizeProviderCatalog}. Reconciliation keys strictly on
 * {@code (providerType, providerTripId)} identity — see that port's Javadoc.
 *
 * The exact date window searched per route is an implementation decision
 * (docs/services/inventory-service/use-cases.md defers "exact matching heuristic" and search
 * scope); this implementation searches a configurable number of days forward from today, per
 * known {@code Route} — a reasonable, bounded default, not a documented requirement.
 */
public class SynchronizeProviderCatalogService implements SynchronizeProviderCatalog {

    private static final Logger log = LoggerFactory.getLogger(SynchronizeProviderCatalogService.class);

    private final RouteRepository routeRepository;
    private final CityRepository cityRepository;
    private final TripRepository tripRepository;
    private final SeatLayoutRepository seatLayoutRepository;
    private final ProviderMappingRepository providerMappingRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final CatalogEventPublisher catalogEventPublisher;
    private final Clock clock;
    private final int syncWindowDays;

    public SynchronizeProviderCatalogService(RouteRepository routeRepository, CityRepository cityRepository,
                                              TripRepository tripRepository, SeatLayoutRepository seatLayoutRepository,
                                              ProviderMappingRepository providerMappingRepository,
                                              SyncRecordRepository syncRecordRepository,
                                              ProviderIntegrationClient providerIntegrationClient,
                                              CatalogEventPublisher catalogEventPublisher, Clock clock,
                                              int syncWindowDays) {
        this.routeRepository = routeRepository;
        this.cityRepository = cityRepository;
        this.tripRepository = tripRepository;
        this.seatLayoutRepository = seatLayoutRepository;
        this.providerMappingRepository = providerMappingRepository;
        this.syncRecordRepository = syncRecordRepository;
        this.providerIntegrationClient = providerIntegrationClient;
        this.catalogEventPublisher = catalogEventPublisher;
        this.clock = clock;
        this.syncWindowDays = syncWindowDays;
    }

    @Override
    public Result synchronize(Command command) {
        ProviderType providerType = command.providerType();
        Instant startedAt = clock.instant();
        SyncRecord record = SyncRecord.start(SyncRecordId.generate(), providerType, startedAt);
        syncRecordRepository.save(record);

        try {
            int reconciled = 0;
            for (Route route : routeRepository.findAll()) {
                reconciled += synchronizeRoute(providerType, route);
            }
            Instant completedAt = clock.instant();
            record.complete(completedAt);
            syncRecordRepository.save(record);
            catalogEventPublisher.publishCatalogSyncCompleted(providerType, reconciled, record.catalogVersion(), completedAt);
            return new Result(reconciled, true);
        } catch (RuntimeException e) {
            log.error("Catalog synchronization failed for provider {}", providerType, e);
            Instant failedAt = clock.instant();
            record.fail(failedAt, e.getMessage());
            syncRecordRepository.save(record);
            catalogEventPublisher.publishCatalogSyncFailed(providerType, e.getMessage(), failedAt);
            return new Result(0, false);
        }
    }

    private int synchronizeRoute(ProviderType providerType, Route route) {
        Optional<City> origin = cityRepository.findById(route.originCityId());
        Optional<City> destination = cityRepository.findById(route.destinationCityId());
        if (origin.isEmpty() || destination.isEmpty()) {
            log.warn("Route {} references a missing city — skipping for provider {}", route.id(), providerType);
            return 0;
        }

        int reconciled = 0;
        for (int offset = 0; offset < syncWindowDays; offset++) {
            LocalDate date = LocalDate.now(clock).plusDays(offset);
            List<ProviderIntegrationClient.ExternalProviderTrip> results =
                    providerIntegrationClient.searchTrips(providerType, origin.get().name(), destination.get().name(), date);
            for (ProviderIntegrationClient.ExternalProviderTrip result : results) {
                if (reconcileTrip(providerType, route.id(), result)) {
                    reconciled++;
                }
            }
        }
        return reconciled;
    }

    private boolean reconcileTrip(ProviderType providerType, RouteId routeId, ProviderIntegrationClient.ExternalProviderTrip result) {
        Instant now = clock.instant();
        Optional<ProviderMapping> existingMapping =
                providerMappingRepository.findByProviderTypeAndProviderTripId(providerType, result.providerTripId());

        if (existingMapping.isPresent()) {
            Trip trip = tripRepository.findById(existingMapping.get().tripId()).orElse(null);
            if (trip == null) {
                log.warn("ProviderMapping for {}/{} references a missing trip — skipping",
                        providerType, result.providerTripId());
                return false;
            }
            boolean applied = trip.applyUpdate(routeId, result.origin(), result.destination(),
                    new TripSchedule(result.departureTime(), result.arrivalTime()), result.operatorName(),
                    result.busType(), List.of(), fareOf(result, now), now);
            if (!applied) {
                return false;
            }
            tripRepository.save(trip);
            existingMapping.get().recordSync(now, com.roadscanner.inventoryservice.domain.model.SyncStatus.SUCCESS);
            providerMappingRepository.save(existingMapping.get());
            catalogEventPublisher.publishTripUpdated(trip, now);
            return true;
        }

        TripId tripId = TripId.generate();
        Trip trip = Trip.createFromProviderSync(tripId, routeId, result.origin(), result.destination(),
                new TripSchedule(result.departureTime(), result.arrivalTime()), result.operatorName(),
                result.busType(), fareOf(result, now), now);
        tripRepository.save(trip);

        providerIntegrationClient.getSeatLayout(providerType, result.providerTripId()).ifPresentOrElse(
                layout -> {
                    List<Seat> seats = layout.seats().stream()
                            .map(s -> new Seat(new SeatNumber(s.seatNumber()), s.deck(), s.seatType(), false, null))
                            .toList();
                    seatLayoutRepository.save(new SeatLayout(tripId, seats));
                },
                () -> log.warn("No seat layout available from provider {} for {} — trip {} created without one",
                        providerType, result.providerTripId(), tripId)
        );

        providerMappingRepository.save(ProviderMapping.create(tripId, providerType, result.providerTripId(), now));
        catalogEventPublisher.publishTripPublished(trip, now);
        return true;
    }

    private FareAmount fareOf(ProviderIntegrationClient.ExternalProviderTrip result, Instant now) {
        return new FareAmount(result.fareAmount(), Currency.getInstance(result.fareCurrency()), now);
    }
}
