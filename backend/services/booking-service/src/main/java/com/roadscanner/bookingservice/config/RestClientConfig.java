package com.roadscanner.bookingservice.config;

import com.roadscanner.bookingservice.adapter.out.client.InventoryServiceProperties;
import com.roadscanner.bookingservice.adapter.out.client.ProviderIntegrationServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The two {@link RestClient} beans {@code adapter.out.client}'s adapters use — matching
 * {@code inventory-service}'s {@code ProviderIntegrationClientConfig} pattern exactly, one per
 * outbound service this service is allowed to call
 * (docs/services/booking-service/boundaries.md). */
@Configuration
@EnableConfigurationProperties({InventoryServiceProperties.class, ProviderIntegrationServiceProperties.class})
public class RestClientConfig {

    @Bean
    public RestClient inventoryServiceRestClient(InventoryServiceProperties properties) {
        return buildClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient providerIntegrationRestClient(ProviderIntegrationServiceProperties properties) {
        return buildClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    private RestClient buildClient(String baseUrl, java.time.Duration connectTimeout, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
