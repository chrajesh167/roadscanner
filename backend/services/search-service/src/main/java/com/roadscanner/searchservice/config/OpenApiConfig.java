package com.roadscanner.searchservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes an OpenAPI spec from day one, per NFR-15 and {@code .claude/ARCHITECTURE_RULES.md}
 * — "every service must expose OpenAPI." No security scheme is declared here (unlike
 * {@code auth-service}'s {@code OpenApiConfig}): this service does not implement authentication
 * or authorization itself — see docs/services/search-service/responsibilities.md and this
 * service's README "Remaining Integration Points" for where that responsibility sits instead
 * ({@code api-gateway} authenticates every request before it ever reaches this service).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI searchServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RoadScanner Search Service")
                        .description("Trip search, ranking, and filtering over a derived read model — "
                                + "see docs/services/search-service/overview.md")
                        .version("0.1.0"));
    }
}
