package com.roadscanner.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient} used to reach provider-integration-service, built exactly as
 * {@link InventoryClientConfig} builds its own — same explicit-timeouts discipline, since an
 * unbounded read would let one slow provider hold a search thread indefinitely.
 */
@Configuration
public class ProviderIntegrationClientConfig {

    @Bean
    public RestClient providerIntegrationRestClient(ProviderIntegrationServiceProperties properties) {
        int timeoutMillis = (int) properties.requestTimeout().toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
