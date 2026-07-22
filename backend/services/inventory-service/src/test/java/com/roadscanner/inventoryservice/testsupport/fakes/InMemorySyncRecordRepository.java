package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.SyncRecord;
import com.roadscanner.inventoryservice.domain.model.SyncRecordId;
import com.roadscanner.inventoryservice.domain.port.out.SyncRecordRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySyncRecordRepository implements SyncRecordRepository {

    private final Map<SyncRecordId, SyncRecord> records = new LinkedHashMap<>();

    @Override
    public Optional<SyncRecord> findLatestByProviderType(ProviderType providerType) {
        return records.values().stream()
                .filter(r -> r.providerType().equals(providerType))
                .max((a, b) -> a.lastAttemptAt().compareTo(b.lastAttemptAt()));
    }

    @Override
    public List<SyncRecord> findAllLatest() {
        return records.values().stream().toList();
    }

    @Override
    public SyncRecord save(SyncRecord record) {
        records.put(record.id(), record);
        return record;
    }
}
