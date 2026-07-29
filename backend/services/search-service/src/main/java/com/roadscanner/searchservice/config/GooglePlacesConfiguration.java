package com.roadscanner.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient} used to call Google Places, built the same way
 * {@link InventoryClientConfig} builds the inventory-service client.
 *
 * <p>Both timeouts are set explicitly. Autocomplete sits on the interactive path — a keystroke
 * waiting on an unbounded socket read would hold a request thread indefinitely and make a slow
 * Google far worse than an unavailable one.
 *
 * <p>The API key is deliberately <em>not</em> baked into this client as a default header. It is
 * attached per request by the adapter, so that this bean can exist (and the context can start)
 * even when no key is configured — which is what lets the local and test profiles run with the
 * integration disabled.
 */
@Configuration
public class GooglePlacesConfiguration {

    @Bean
    public RestClient googlePlacesRestClient(GooglePlacesProperties properties) {
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
