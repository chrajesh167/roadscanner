package com.roadscanner.inventoryservice.config;

import com.roadscanner.inventoryservice.application.usecase.availability.GetTripAvailabilityService;
import com.roadscanner.inventoryservice.application.usecase.catalog.BrowseCitiesService;
import com.roadscanner.inventoryservice.application.usecase.catalog.BrowseStationsService;
import com.roadscanner.inventoryservice.application.usecase.catalog.GetProviderMappingService;
import com.roadscanner.inventoryservice.application.usecase.catalog.GetSeatLayoutService;
import com.roadscanner.inventoryservice.application.usecase.catalog.GetSyncStatusService;
import com.roadscanner.inventoryservice.application.usecase.catalog.GetTripMetadataService;
import com.roadscanner.inventoryservice.application.usecase.ingestion.IngestCancelledTripService;
import com.roadscanner.inventoryservice.application.usecase.ingestion.IngestOperatorUpdateService;
import com.roadscanner.inventoryservice.application.usecase.ingestion.IngestPublishedTripService;
import com.roadscanner.inventoryservice.application.usecase.ingestion.IngestRouteUpdateService;
import com.roadscanner.inventoryservice.application.usecase.ingestion.IngestUpdatedTripService;
import com.roadscanner.inventoryservice.application.usecase.sync.CatalogSyncCoordinator;
import com.roadscanner.inventoryservice.application.usecase.sync.SynchronizeProviderCatalogService;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.in.BrowseCities;
import com.roadscanner.inventoryservice.domain.port.in.BrowseStations;
import com.roadscanner.inventoryservice.domain.port.in.GetProviderMapping;
import com.roadscanner.inventoryservice.domain.port.in.GetSeatLayout;
import com.roadscanner.inventoryservice.domain.port.in.GetSyncStatus;
import com.roadscanner.inventoryservice.domain.port.in.GetTripAvailability;
import com.roadscanner.inventoryservice.domain.port.in.GetTripMetadata;
import com.roadscanner.inventoryservice.domain.port.in.IngestCancelledTrip;
import com.roadscanner.inventoryservice.domain.port.in.IngestOperatorUpdate;
import com.roadscanner.inventoryservice.domain.port.in.IngestPublishedTrip;
import com.roadscanner.inventoryservice.domain.port.in.IngestRouteUpdate;
import com.roadscanner.inventoryservice.domain.port.in.IngestUpdatedTrip;
import com.roadscanner.inventoryservice.domain.port.in.SynchronizeProviderCatalog;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;
import com.roadscanner.inventoryservice.domain.port.out.CityRepository;
import com.roadscanner.inventoryservice.domain.port.out.OperatorRefRepository;
import com.roadscanner.inventoryservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.inventoryservice.domain.port.out.ProviderMappingRepository;
import com.roadscanner.inventoryservice.domain.port.out.RouteRepository;
import com.roadscanner.inventoryservice.domain.port.out.SeatLayoutRepository;
import com.roadscanner.inventoryservice.domain.port.out.StationRepository;
import com.roadscanner.inventoryservice.domain.port.out.SyncRecordRepository;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/** Explicit bean wiring for every application-layer use case — matching every other service in
 * this codebase's identical {@code UseCaseConfig} convention: plain constructors, no Spring
 * stereotype annotations on the application classes themselves. */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public BrowseCities browseCities(CityRepository cityRepository) {
        return new BrowseCitiesService(cityRepository);
    }

    @Bean
    public BrowseStations browseStations(StationRepository stationRepository) {
        return new BrowseStationsService(stationRepository);
    }

    @Bean
    public GetTripMetadata getTripMetadata(TripRepository tripRepository) {
        return new GetTripMetadataService(tripRepository);
    }

    @Bean
    public GetSeatLayout getSeatLayout(SeatLayoutRepository seatLayoutRepository) {
        return new GetSeatLayoutService(seatLayoutRepository);
    }

    @Bean
    public GetProviderMapping getProviderMapping(ProviderMappingRepository providerMappingRepository) {
        return new GetProviderMappingService(providerMappingRepository);
    }

    @Bean
    public GetSyncStatus getSyncStatus(SyncRecordRepository syncRecordRepository) {
        return new GetSyncStatusService(syncRecordRepository);
    }

    @Bean
    public GetTripAvailability getTripAvailability(ProviderMappingRepository providerMappingRepository,
                                                     ProviderIntegrationClient providerIntegrationClient) {
        return new GetTripAvailabilityService(providerMappingRepository, providerIntegrationClient);
    }

    @Bean
    public IngestPublishedTrip ingestPublishedTrip(TripRepository tripRepository, SeatLayoutRepository seatLayoutRepository,
                                                     OperatorRefRepository operatorRefRepository,
                                                     CatalogEventPublisher catalogEventPublisher) {
        return new IngestPublishedTripService(tripRepository, seatLayoutRepository, operatorRefRepository, catalogEventPublisher);
    }

    @Bean
    public IngestUpdatedTrip ingestUpdatedTrip(TripRepository tripRepository, CatalogEventPublisher catalogEventPublisher) {
        return new IngestUpdatedTripService(tripRepository, catalogEventPublisher);
    }

    @Bean
    public IngestCancelledTrip ingestCancelledTrip(TripRepository tripRepository, CatalogEventPublisher catalogEventPublisher) {
        return new IngestCancelledTripService(tripRepository, catalogEventPublisher);
    }

    @Bean
    public IngestOperatorUpdate ingestOperatorUpdate(OperatorRefRepository operatorRefRepository, TripRepository tripRepository,
                                                       CatalogEventPublisher catalogEventPublisher, Clock clock) {
        return new IngestOperatorUpdateService(operatorRefRepository, tripRepository, catalogEventPublisher, clock);
    }

    @Bean
    public IngestRouteUpdate ingestRouteUpdate() {
        return new IngestRouteUpdateService();
    }

    @Bean
    public SynchronizeProviderCatalog synchronizeProviderCatalog(RouteRepository routeRepository, CityRepository cityRepository,
                                                                    TripRepository tripRepository, SeatLayoutRepository seatLayoutRepository,
                                                                    ProviderMappingRepository providerMappingRepository,
                                                                    SyncRecordRepository syncRecordRepository,
                                                                    ProviderIntegrationClient providerIntegrationClient,
                                                                    CatalogEventPublisher catalogEventPublisher, Clock clock,
                                                                    InventoryProperties properties) {
        return new SynchronizeProviderCatalogService(routeRepository, cityRepository, tripRepository, seatLayoutRepository,
                providerMappingRepository, syncRecordRepository, providerIntegrationClient, catalogEventPublisher, clock,
                properties.sync().windowDays());
    }

    @Bean
    public CatalogSyncCoordinator catalogSyncCoordinator(SynchronizeProviderCatalog synchronizeProviderCatalog,
                                                           InventoryProperties properties) {
        List<ProviderType> providerTypes = properties.sync().providerTypes().stream().map(ProviderType::new).toList();
        return new CatalogSyncCoordinator(synchronizeProviderCatalog, providerTypes);
    }
}
