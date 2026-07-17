package com.roadscanner.searchservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is configured here, not left to per-environment reverse-proxy rules, so the same policy
 * applies whether a request reaches this service directly (local dev) or via {@code api-gateway}.
 * Allowed origins are externalized (see application-*.yml) rather than hardcoded — matching
 * {@code auth-service}'s identical {@code CorsConfig}.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${roadscanner.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
