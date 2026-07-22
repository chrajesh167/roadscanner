package com.roadscanner.inventoryservice.adapter.in.rest.sync;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.in.GetSyncStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operational visibility into catalog synchronization health
 * (docs/services/inventory-service/api-summary.md). */
@RestController
@RequestMapping("/api/v1/inventory/sync")
@Tag(name = "Catalog Synchronization", description = "Operational visibility into provider catalog sync")
class SyncController {

    private final GetSyncStatus getSyncStatus;

    SyncController(GetSyncStatus getSyncStatus) {
        this.getSyncStatus = getSyncStatus;
    }

    @GetMapping("/status")
    @Operation(summary = "Get catalog sync status", description = "Latest synchronization attempt per provider, or filtered to one provider.")
    SyncStatusResponse getStatus(@RequestParam(required = false) String providerType) {
        GetSyncStatus.Result result = getSyncStatus.get(
                new GetSyncStatus.Command(providerType != null ? new ProviderType(providerType) : null));
        return SyncStatusResponse.from(result);
    }
}
