package com.roadscanner.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Operational tuning knobs — {@code roadscanner.inventory.*} in {@code application.yml}.
 * Matching {@code search-service}'s {@code SearchProperties} convention. */
@ConfigurationProperties(prefix = "roadscanner.inventory")
public record InventoryProperties(Kafka kafka, Sync sync, Pagination pagination) {

    public record Kafka(String operatorTripEventsTopic, String operatorRouteEventsTopic,
                         String operatorOperatorEventsTopic, String catalogTripEventsTopic,
                         String catalogOperatorEventsTopic, String catalogSyncEventsTopic) {
    }

    /** {@code providerTypes} is this service's own configured candidate list for catalog
     * synchronization — see {@code CatalogSyncCoordinator}'s Javadoc for why this isn't fetched
     * from {@code provider-integration-service} itself. */
    public record Sync(List<String> providerTypes, int windowDays, String scheduleInterval) {
    }

    public record Pagination(int defaultLimit, int maxLimit) {
    }
}
