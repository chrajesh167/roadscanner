package com.roadscanner.bookingservice.adapter.out.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "roadscanner.booking.provider-integration-service")
public record ProviderIntegrationServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
