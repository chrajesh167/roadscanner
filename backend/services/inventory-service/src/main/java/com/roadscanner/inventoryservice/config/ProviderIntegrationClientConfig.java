package com.roadscanner.inventoryservice.config;

import com.roadscanner.inventoryservice.adapter.out.client.ProviderIntegrationServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The {@link RestClient} used by {@code adapter.out.client.ProviderIntegrationClientAdapter} —
 * matching {@code search-service}'s {@code InventoryClientConfig} pattern exactly. */
@Configuration
public class ProviderIntegrationClientConfig {

    @Bean
    public RestClient providerIntegrationRestClient(ProviderIntegrationServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
