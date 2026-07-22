package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.SyncRecord;

import java.util.List;
import java.util.Objects;

/** Operational visibility into catalog synchronization health
 * (docs/services/inventory-service/use-cases.md). */
public interface GetSyncStatus {

    Result get(Command command);

    record Command(ProviderType providerType) {
        // providerType may be null — a null filter returns every provider's latest SyncRecord.
    }

    record Result(List<SyncRecord> records) {
        public Result {
            Objects.requireNonNull(records, "records must not be null");
            records = List.copyOf(records);
        }
    }
}
