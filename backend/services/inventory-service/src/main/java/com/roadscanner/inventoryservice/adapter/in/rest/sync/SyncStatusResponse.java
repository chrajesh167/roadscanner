package com.roadscanner.inventoryservice.adapter.in.rest.sync;

import com.roadscanner.inventoryservice.domain.port.in.GetSyncStatus;

import java.util.List;

public record SyncStatusResponse(List<SyncRecordResponse> records) {

    public static SyncStatusResponse from(GetSyncStatus.Result result) {
        return new SyncStatusResponse(result.records().stream().map(SyncRecordResponse::from).toList());
    }
}
