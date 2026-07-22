package com.roadscanner.inventoryservice.adapter.in.rest.providermapping;

import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.GetProviderMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal, service-to-service only — consumed by {@code booking-service} to resolve which
 * provider (if any) backs a catalog trip (docs/services/inventory-service/api-summary.md). */
@RestController
@RequestMapping("/api/v1/inventory/trips/{tripId}/provider-mapping")
@Tag(name = "Provider Mapping", description = "Resolve a catalog trip's provider mapping")
class ProviderMappingController {

    private final GetProviderMapping getProviderMapping;

    ProviderMappingController(GetProviderMapping getProviderMapping) {
        this.getProviderMapping = getProviderMapping;
    }

    @GetMapping
    @Operation(summary = "Get provider mapping", description = "Provider type + native trip id for a catalog trip.")
    ProviderMappingResponse get(@PathVariable UUID tripId) {
        GetProviderMapping.Result result = getProviderMapping.get(new GetProviderMapping.Command(new TripId(tripId)));
        return ProviderMappingResponse.from(result.mapping());
    }
}
