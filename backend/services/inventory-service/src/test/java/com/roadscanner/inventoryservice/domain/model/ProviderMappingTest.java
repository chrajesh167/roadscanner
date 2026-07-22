package com.roadscanner.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderMappingTest {

    @Test
    void recordSyncUpdatesTimestampAndStatus() {
        Instant t0 = Instant.parse("2026-07-01T00:00:00Z");
        ProviderMapping mapping = ProviderMapping.create(TripId.generate(), new ProviderType("MOCK"), "MOCK-TRIP-1", t0);

        Instant t1 = t0.plusSeconds(60);
        mapping.recordSync(t1, SyncStatus.FAILED);

        assertThat(mapping.lastSyncedAt()).isEqualTo(t1);
        assertThat(mapping.syncStatus()).isEqualTo(SyncStatus.FAILED);
    }
}
