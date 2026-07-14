package com.roadscanner.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes an OpenAPI spec from day one, per NFR-15 (docs/requirements/non-functional-requirements.md)
 * and .claude/ARCHITECTURE_RULES.md — "every service must expose OpenAPI" is not deferred until
 * controllers exist. The spec is metadata-only today; springdoc generates the actual paths from
 * controllers as they're added (docs/services/auth-service/implementation-roadmap.md step 7).
 *
 * The bearer-JWT security scheme is declared now so future controllers only need
 * @SecurityRequirement("bearer-jwt"), not a repeated scheme definition per endpoint.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RoadScanner Auth Service")
                        .description("Authentication and authorization only — see docs/services/auth-service/overview.md")
                        .version("0.1.0"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
