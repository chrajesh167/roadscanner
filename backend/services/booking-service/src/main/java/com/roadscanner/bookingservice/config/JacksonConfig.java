package com.roadscanner.bookingservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Platform-wide JSON conventions — matching every other service's identical
 * {@code JacksonConfig}. {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled deliberately matters here:
 * this service's inbound Kafka DTOs are its own, independently-maintained copies of upstream
 * wire shapes (docs/services/booking-service/events-consumed.md), so an upstream field this
 * service doesn't care about must never break deserialization. */
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
