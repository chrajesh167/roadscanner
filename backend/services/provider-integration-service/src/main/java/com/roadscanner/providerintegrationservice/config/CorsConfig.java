package com.roadscanner.providerintegrationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** This service's entire surface is {@code /internal/**} (no browser ever calls it directly —
 * see README.md "API Surface"), so this exists only for parity with the platform's other
 * services and to keep local tooling (e.g. Swagger UI) consistent, not because a browser client
 * is expected to call it cross-origin in production. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${roadscanner.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
