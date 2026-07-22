package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RecordingCatalogEventPublisher implements CatalogEventPublisher {

    public record Published(String eventType, Trip trip) {
    }

    private final List<Published> tripEvents = new ArrayList<>();
    private int syncCompletedCount;
    private int syncFailedCount;

    @Override
    public void publishTripPublished(Trip trip, Instant occurredAt) {
        tripEvents.add(new Published("TripPublished", trip));
    }

    @Override
    public void publishTripUpdated(Trip trip, Instant occurredAt) {
        tripEvents.add(new Published("TripUpdated", trip));
    }

    @Override
    public void publishTripCancelled(Trip trip, Instant occurredAt) {
        tripEvents.add(new Published("TripCancelled", trip));
    }

    @Override
    public void publishOperatorUpdated(UUID operatorId, String displayName, Instant occurredAt) {
        // not recorded — no test currently asserts on this
    }

    @Override
    public void publishCatalogSyncCompleted(ProviderType providerType, int tripsReconciled, long catalogVersion, Instant occurredAt) {
        syncCompletedCount++;
    }

    @Override
    public void publishCatalogSyncFailed(ProviderType providerType, String errorDetail, Instant occurredAt) {
        syncFailedCount++;
    }

    public List<Published> tripEvents() {
        return List.copyOf(tripEvents);
    }

    public int syncCompletedCount() {
        return syncCompletedCount;
    }

    public int syncFailedCount() {
        return syncFailedCount;
    }
}
