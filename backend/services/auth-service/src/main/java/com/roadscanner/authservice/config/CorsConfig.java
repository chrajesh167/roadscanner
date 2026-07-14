package com.roadscanner.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is configured here, not left to per-environment reverse-proxy rules, so the same policy
 * applies whether a request reaches this service directly (local dev) or via api-gateway (every
 * other environment). Allowed origins are externalized (see application-*.yml) rather than
 * hardcoded, since customer-web/operator-portal/admin-console origins differ per environment.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${roadscanner.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
