package com.roadscanner.inventoryservice.adapter.in.rest.sync;

import com.roadscanner.inventoryservice.domain.model.SyncRecord;

import java.time.Instant;

public record SyncRecordResponse(String providerType, Instant lastAttemptAt, String status, long catalogVersion,
                                  String errorDetail) {

    public static SyncRecordResponse from(SyncRecord record) {
        return new SyncRecordResponse(record.providerType().code(), record.lastAttemptAt(), record.status().name(),
                record.catalogVersion(), record.errorDetail().orElse(null));
    }
}
