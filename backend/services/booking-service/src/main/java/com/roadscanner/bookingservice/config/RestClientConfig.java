package com.roadscanner.bookingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.bookingservice.adapter.out.client.InventoryServiceProperties;
import com.roadscanner.bookingservice.adapter.out.client.ProviderIntegrationServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/** The two {@link RestClient} beans {@code adapter.out.client}'s adapters use — matching
 * {@code inventory-service}'s {@code ProviderIntegrationClientConfig} pattern exactly, one per
 * outbound service this service is allowed to call
 * (docs/services/booking-service/boundaries.md). */
@Configuration
@EnableConfigurationProperties({InventoryServiceProperties.class, ProviderIntegrationServiceProperties.class})
public class RestClientConfig {

    @Bean
    public RestClient inventoryServiceRestClient(InventoryServiceProperties properties, ObjectMapper objectMapper) {
        return buildClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), objectMapper);
    }

    @Bean
    public RestClient providerIntegrationRestClient(ProviderIntegrationServiceProperties properties,
                                                     ObjectMapper objectMapper) {
        return buildClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), objectMapper);
    }

    /**
     * Built with the application's own {@link ObjectMapper}, not the one {@code RestClient.builder()}
     * creates for itself.
     *
     * <p>That default mapper is not Spring Boot's: it keeps {@code WRITE_DATES_AS_TIMESTAMPS}
     * enabled, so a {@code LocalDate} left this service as {@code [1994,3,17]} instead of
     * {@code "1994-03-17"}. Nothing noticed until a passenger's birth date had to cross this
     * boundary — {@code provider-integration-service} binds {@code LocalDate} from an ISO string
     * and would have rejected every seat block. Sharing the configured mapper makes the wire format
     * here identical to the one this service accepts on its own inbound API.
     */
    private RestClient buildClient(String baseUrl, java.time.Duration connectTimeout,
                                    java.time.Duration readTimeout, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }
}
