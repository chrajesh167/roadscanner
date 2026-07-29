package com.roadscanner.providerintegrationservice.testsupport.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Supplies the {@link JwtDecoder} bean {@code config.SecurityConfig}'s filter chain requires at
 * context-startup time, for {@code @WebMvcTest} slices that authenticate via Spring Security
 * Test's {@code SecurityMockMvcRequestPostProcessors.jwt()} — which injects a pre-built
 * {@code Authentication} directly into the security context and therefore never calls the
 * decoder. Copied from {@code booking-service}'s identical test-support class.
 *
 * <p>It throws rather than returning null so that a test which accidentally depends on real
 * token decoding fails loudly instead of silently authenticating as nobody.
 */
@TestConfiguration
public class NoOpJwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                    "Not expected to be called — tests authenticate via SecurityMockMvcRequestPostProcessors.jwt()");
        };
    }
}
