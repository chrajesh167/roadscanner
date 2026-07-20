package com.roadscanner.providerintegrationservice.adapter.in.rest.capability;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.GetProviderCapabilities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal-only — capability discovery, no session required. */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/capabilities")
@Tag(name = "Provider Capabilities", description = "Discover what a provider supports")
class ProviderCapabilityController {

    private final GetProviderCapabilities getProviderCapabilities;

    ProviderCapabilityController(GetProviderCapabilities getProviderCapabilities) {
        this.getProviderCapabilities = getProviderCapabilities;
    }

    @GetMapping
    @Operation(summary = "Get provider capabilities", description = "The intersection of what's configured and what the resolved adapter actually implements.")
    CapabilitiesResponse getCapabilities(@PathVariable String providerType) {
        GetProviderCapabilities.Result result = getProviderCapabilities.getCapabilities(
                new GetProviderCapabilities.Command(new ProviderType(providerType)));
        return CapabilitiesResponse.from(result);
    }
}
