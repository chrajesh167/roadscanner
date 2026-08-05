package com.roadscanner.providerintegrationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin policy for the browser-facing half of this service.
 *
 * <p>The {@code /internal/**} surface is service-to-service and no browser calls it, but
 * {@code /api/v1/providers/**} is explicitly "intended to be reachable by a human"
 * ({@code ProviderAdminController}) — the admin console is a browser client on another origin,
 * so the methods listed here are load-bearing rather than decorative.
 *
 * <p>{@code PUT} is included because two admin routes are {@code PUT} and nothing else can
 * express them: updating a provider's configuration, and replacing its credentials. Omitting it
 * fails the preflight, so those two routes are unreachable from a browser however correct the
 * request itself is. {@code auth-service}'s equivalent configuration already allows it.
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
