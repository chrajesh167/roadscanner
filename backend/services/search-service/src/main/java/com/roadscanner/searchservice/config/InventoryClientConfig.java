package com.roadscanner.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient} used by {@code adapter.out.client.InventoryAvailabilityClientAdapter}.
 * Connect and read timeouts are both bound to {@code roadscanner.search.availability.request-timeout}
 * — deliberately short (docs/services/search-service/boundaries.md's "degrade, not fail" rule
 * depends on this call never blocking a search for long; the adapter degrades to
 * {@code AvailabilityStatus.unknown()} on any timeout rather than propagating it).
 */
@Configuration
public class InventoryClientConfig {

    @Bean
    public RestClient inventoryServiceRestClient(InventoryServiceProperties inventoryServiceProperties,
                                                 SearchProperties searchProperties) {
        int timeoutMillis = (int) searchProperties.availability().requestTimeout().toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        return RestClient.builder()
                .baseUrl(inventoryServiceProperties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
