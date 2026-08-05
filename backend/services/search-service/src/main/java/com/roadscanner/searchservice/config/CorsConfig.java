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
 *
 * <p>The method list has to cover the whole surface, not just the public read half. This service
 * exposes four administrative routes that a browser is expected to call — {@code PUT} and
 * {@code DELETE} on {@code /api/v1/locations/{id}}, and the same pair on
 * {@code /api/v1/provider-mappings/{id}}. Omitting a method fails the preflight, which makes those
 * routes unreachable from any browser however correct the request itself is, while leaving every
 * server-side test green: {@code MockMvc} invokes handlers directly and never issues a preflight.
 * {@code ProviderMappingCorsTest} closes that gap.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${roadscanner.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
