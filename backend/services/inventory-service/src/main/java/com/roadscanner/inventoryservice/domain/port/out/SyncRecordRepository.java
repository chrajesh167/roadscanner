package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.SyncRecord;

import java.util.List;
import java.util.Optional;

public interface SyncRecordRepository {

    Optional<SyncRecord> findLatestByProviderType(ProviderType providerType);

    List<SyncRecord> findAllLatest();

    SyncRecord save(SyncRecord record);
}
