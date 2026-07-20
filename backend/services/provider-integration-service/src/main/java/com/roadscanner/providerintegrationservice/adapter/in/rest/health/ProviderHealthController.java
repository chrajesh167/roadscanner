package com.roadscanner.providerintegrationservice.adapter.in.rest.health;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal-only — on-demand health probe, no session required. Also driven on a schedule by
 * {@code ProviderHealthMonitor} (see {@code config}) — see {@code CheckProviderHealth}'s Javadoc
 * for why both share one implementation. */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/health")
@Tag(name = "Provider Health", description = "On-demand provider health probe")
class ProviderHealthController {

    private final CheckProviderHealth checkProviderHealth;

    ProviderHealthController(CheckProviderHealth checkProviderHealth) {
        this.checkProviderHealth = checkProviderHealth;
    }

    @GetMapping
    @Operation(summary = "Check provider health", description = "Performs a live probe and returns the resulting durable health record.")
    ProviderHealthResponse checkHealth(@PathVariable String providerType) {
        CheckProviderHealth.Result result = checkProviderHealth.check(
                new CheckProviderHealth.Command(new ProviderType(providerType)));
        return ProviderHealthResponse.from(result.health());
    }
}
