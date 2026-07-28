package com.roadscanner.paymentservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Platform-wide JSON conventions — matching every other service's identical {@code JacksonConfig}.
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled deliberately: inbound Kafka DTOs are this service's
 * own independently-maintained copies of upstream wire shapes, so an upstream field this service
 * does not care about must never break deserialization (and, symmetrically, the enrichment fields
 * this service adds to {@code payment-events} are safely ignored by {@code booking-service}). */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
