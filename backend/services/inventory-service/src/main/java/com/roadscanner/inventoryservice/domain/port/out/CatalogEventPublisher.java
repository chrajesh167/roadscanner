package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.Trip;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes this service's own catalog events — the merged-catalog {@code TripPublished}/
 * {@code TripUpdated}/{@code TripCancelled} trio (wire-compatible with {@code search-service}'s
 * already-shipped {@code TripEventMessage}, so a future topic-source config swap on that side
 * needs no code change — docs/services/inventory-service/events-published.md), plus
 * {@code OperatorUpdated} and catalog-synchronization observability events.
 *
 * {@code RouteUpdated} is deliberately not part of this port — see
 * {@code IngestRouteUpdate}'s Javadoc for why that event is acknowledgment-only in this
 * implementation. {@code FareSnapshotUpdated} is deliberately folded into
 * {@link #publishTripUpdated(Trip, Instant)} rather than a separate message — a fare change is a
 * trip update, and no consumer needs the two distinguished today.
 */
public interface CatalogEventPublisher {

    void publishTripPublished(Trip trip, Instant occurredAt);

    void publishTripUpdated(Trip trip, Instant occurredAt);

    void publishTripCancelled(Trip trip, Instant occurredAt);

    void publishOperatorUpdated(UUID operatorId, String displayName, Instant occurredAt);

    void publishCatalogSyncCompleted(ProviderType providerType, int tripsReconciled, long catalogVersion, Instant occurredAt);

    void publishCatalogSyncFailed(ProviderType providerType, String errorDetail, Instant occurredAt);
}
