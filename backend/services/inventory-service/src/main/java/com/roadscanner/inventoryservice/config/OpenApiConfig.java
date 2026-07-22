package com.roadscanner.inventoryservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RoadScanner Inventory Service")
                        .description("The platform's catalog and metadata service — "
                                + "see docs/services/inventory-service/overview.md")
                        .version("0.1.0"));
    }
}
