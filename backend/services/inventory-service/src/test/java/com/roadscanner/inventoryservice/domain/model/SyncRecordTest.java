package com.roadscanner.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRecordTest {

    private static final Instant T0 = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void completeAdvancesCatalogVersionAndClearsError() {
        SyncRecord record = SyncRecord.start(SyncRecordId.generate(), new ProviderType("MOCK"), T0);
        record.fail(T0.plusSeconds(10), "boom");

        record.complete(T0.plusSeconds(20));

        assertThat(record.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(record.catalogVersion()).isEqualTo(1L);
        assertThat(record.errorDetail()).isEmpty();
    }

    @Test
    void failRecordsErrorDetail() {
        SyncRecord record = SyncRecord.start(SyncRecordId.generate(), new ProviderType("MOCK"), T0);

        record.fail(T0.plusSeconds(10), "provider unreachable");

        assertThat(record.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(record.errorDetail()).contains("provider unreachable");
    }
}
