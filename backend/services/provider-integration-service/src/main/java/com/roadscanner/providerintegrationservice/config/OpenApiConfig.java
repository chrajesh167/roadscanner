package com.roadscanner.providerintegrationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes an OpenAPI spec from day one, per NFR-15 and {@code .claude/ARCHITECTURE_RULES.md}
 * — "every service must expose OpenAPI." No security scheme declared, matching
 * {@code search-service}'s identical reasoning: this service implements no authentication
 * itself (see README.md "Remaining Integration Points"). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI providerIntegrationServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RoadScanner Provider Integration Service")
                        .description("The platform's sole gateway to external transportation providers — "
                                + "see docs/services/provider-integration-service/overview.md")
                        .version("0.1.0"));
    }
}
