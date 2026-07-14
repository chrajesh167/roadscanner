package com.roadscanner.authservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Platform-wide JSON conventions: ISO-8601 timestamps (never epoch millis), and tolerance for
 * unknown fields on deserialization so a client sending an extra field never breaks a request —
 * forward-compatibility for API evolution, not a validation relaxation (structural field
 * validation is a separate, deliberate concern — see docs/services/auth-service/validation-strategy.md).
 */
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
