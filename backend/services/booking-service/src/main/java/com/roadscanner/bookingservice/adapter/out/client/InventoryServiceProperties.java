package com.roadscanner.bookingservice.adapter.out.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "roadscanner.booking.inventory-service")
public record InventoryServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
