package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Wiring for the FlixBus adapter package: the {@link RestClient} (base URL + connect/read
 * timeouts from {@link FlixBusProperties}) and the collaborators every {@code FlixBus*Client}
 * shares ({@link FlixBusMapper}, {@link FlixBusExceptionTranslator}). Resilience4j instance
 * names for this provider ({@code flixbus}) are configured declaratively in
 * {@code application.yml} and applied via annotations directly on the {@code FlixBus*Client}
 * methods — see {@code config.ResilienceConfig} for the shared registry wiring.
 */
@Configuration
class FlixBusConfiguration {

    @Bean
    RestClient flixBusRestClient(FlixBusProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    FlixBusMapper flixBusMapper(Clock clock) {
        return new FlixBusMapper(clock);
    }

    @Bean
    FlixBusExceptionTranslator flixBusExceptionTranslator(Clock clock) {
        return new FlixBusExceptionTranslator(clock);
    }
}
