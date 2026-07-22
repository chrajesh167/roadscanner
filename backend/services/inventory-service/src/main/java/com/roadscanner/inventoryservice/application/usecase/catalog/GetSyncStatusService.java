package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.model.SyncRecord;
import com.roadscanner.inventoryservice.domain.port.in.GetSyncStatus;
import com.roadscanner.inventoryservice.domain.port.out.SyncRecordRepository;

import java.util.List;

/** Implements {@link GetSyncStatus}. */
public class GetSyncStatusService implements GetSyncStatus {

    private final SyncRecordRepository syncRecordRepository;

    public GetSyncStatusService(SyncRecordRepository syncRecordRepository) {
        this.syncRecordRepository = syncRecordRepository;
    }

    @Override
    public Result get(Command command) {
        if (command.providerType() == null) {
            return new Result(syncRecordRepository.findAllLatest());
        }
        List<SyncRecord> records = syncRecordRepository.findLatestByProviderType(command.providerType())
                .map(List::of)
                .orElseGet(List::of);
        return new Result(records);
    }
}
