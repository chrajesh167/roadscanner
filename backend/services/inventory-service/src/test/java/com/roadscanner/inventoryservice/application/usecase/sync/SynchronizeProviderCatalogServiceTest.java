package com.roadscanner.inventoryservice.application.usecase.sync;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.Route;
import com.roadscanner.inventoryservice.domain.model.RouteId;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.SynchronizeProviderCatalog;
import com.roadscanner.inventoryservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.inventoryservice.testsupport.MutableClock;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryCityRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryProviderMappingRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryRouteRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemorySeatLayoutRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemorySyncRecordRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryTripRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.RecordingCatalogEventPublisher;
import com.roadscanner.inventoryservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SynchronizeProviderCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    private final InMemoryRouteRepository routeRepository = new InMemoryRouteRepository();
    private final InMemoryCityRepository cityRepository = new InMemoryCityRepository();
    private final InMemoryTripRepository tripRepository = new InMemoryTripRepository();
    private final InMemorySeatLayoutRepository seatLayoutRepository = new InMemorySeatLayoutRepository();
    private final InMemoryProviderMappingRepository providerMappingRepository = new InMemoryProviderMappingRepository();
    private final InMemorySyncRecordRepository syncRecordRepository = new InMemorySyncRecordRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final RecordingCatalogEventPublisher catalogEventPublisher = new RecordingCatalogEventPublisher();
    private final MutableClock clock = new MutableClock(NOW);

    private final CityId originCityId = CityId.generate();
    private final CityId destinationCityId = CityId.generate();
    private final RouteId routeId = RouteId.generate();

    private SynchronizeProviderCatalogService service(int windowDays) {
        cityRepository.add(City.create(originCityId, "Chennai", "Tamil Nadu", "India"));
        cityRepository.add(City.create(destinationCityId, "Bengaluru", "Karnataka", "India"));
        routeRepository.add(Route.create(routeId, originCityId, destinationCityId, 350.0));

        return new SynchronizeProviderCatalogService(routeRepository, cityRepository, tripRepository,
                seatLayoutRepository, providerMappingRepository, syncRecordRepository, providerIntegrationClient,
                catalogEventPublisher, clock, windowDays);
    }

    @Test
    void createsANewTripWhenNoMappingExistsYetAndPublishesTripPublished() {
        ProviderIntegrationClient.ExternalProviderTrip externalTrip = new ProviderIntegrationClient.ExternalProviderTrip(
                "MOCK-TRIP-1", "Mock Travels", "Chennai", "Bengaluru", NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                "AC Sleeper", BigDecimal.valueOf(899), "INR", 10);
        providerIntegrationClient.searchTripsResult = (p, d) -> List.of(externalTrip);
        providerIntegrationClient.seatLayoutResult = java.util.Optional.of(
                new ProviderIntegrationClient.ExternalSeatLayout("MOCK-TRIP-1",
                        List.of(new ProviderIntegrationClient.ExternalSeat("L1", "LOWER", "SLEEPER"))));

        SynchronizeProviderCatalog.Result result = service(1).synchronize(
                new SynchronizeProviderCatalog.Command(new ProviderType("MOCK")));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.tripsReconciled()).isEqualTo(1);
        assertThat(providerMappingRepository.findByProviderTypeAndProviderTripId(new ProviderType("MOCK"), "MOCK-TRIP-1")).isPresent();
        assertThat(catalogEventPublisher.tripEvents()).hasSize(1);
        assertThat(catalogEventPublisher.tripEvents().get(0).eventType()).isEqualTo("TripPublished");
        assertThat(catalogEventPublisher.syncCompletedCount()).isEqualTo(1);
    }

    @Test
    void updatesAnExistingTripWhenMappingAlreadyExists() {
        TripId existingTripId = TripId.generate();
        var existingTrip = com.roadscanner.inventoryservice.domain.model.Trip.createFromProviderSync(existingTripId,
                routeId, "Chennai", "Bengaluru", new com.roadscanner.inventoryservice.domain.model.TripSchedule(
                        NOW.plusSeconds(3600), NOW.plusSeconds(7200)), "Mock Travels", "AC Sleeper",
                new com.roadscanner.inventoryservice.domain.model.FareAmount(BigDecimal.valueOf(899),
                        java.util.Currency.getInstance("INR"), NOW), NOW.minusSeconds(1000));
        tripRepository.save(existingTrip);
        providerMappingRepository.save(ProviderMapping.create(existingTripId, new ProviderType("MOCK"), "MOCK-TRIP-1", NOW.minusSeconds(1000)));

        ProviderIntegrationClient.ExternalProviderTrip externalTrip = new ProviderIntegrationClient.ExternalProviderTrip(
                "MOCK-TRIP-1", "Mock Travels", "Chennai", "Bengaluru", NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                "AC Sleeper", BigDecimal.valueOf(999), "INR", 8);
        providerIntegrationClient.searchTripsResult = (p, d) -> List.of(externalTrip);

        SynchronizeProviderCatalog.Result result = service(1).synchronize(
                new SynchronizeProviderCatalog.Command(new ProviderType("MOCK")));

        assertThat(result.tripsReconciled()).isEqualTo(1);
        assertThat(tripRepository.findById(existingTripId)).isPresent();
        assertThat(tripRepository.findById(existingTripId).get().fare().amount()).isEqualByComparingTo("999");
        assertThat(catalogEventPublisher.tripEvents().get(0).eventType()).isEqualTo("TripUpdated");
    }

    @Test
    void publishesCatalogSyncFailedWhenAnUnexpectedExceptionOccurs() {
        // A route referencing a city that isn't in the repository triggers the "skip" path, not
        // failure — to exercise genuine failure, use a route repository that throws.
        var throwingRouteRepository = new com.roadscanner.inventoryservice.domain.port.out.RouteRepository() {
            @Override
            public java.util.Optional<Route> findById(RouteId id) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<Route> findByCities(CityId originCityId, CityId destinationCityId) {
                return java.util.Optional.empty();
            }

            @Override
            public List<Route> findAll() {
                throw new RuntimeException("database unavailable");
            }
        };
        SynchronizeProviderCatalogService failing = new SynchronizeProviderCatalogService(throwingRouteRepository,
                cityRepository, tripRepository, seatLayoutRepository, providerMappingRepository, syncRecordRepository,
                providerIntegrationClient, catalogEventPublisher, clock, 1);

        SynchronizeProviderCatalog.Result result = failing.synchronize(new SynchronizeProviderCatalog.Command(new ProviderType("MOCK")));

        assertThat(result.succeeded()).isFalse();
        assertThat(catalogEventPublisher.syncFailedCount()).isEqualTo(1);
    }
}
