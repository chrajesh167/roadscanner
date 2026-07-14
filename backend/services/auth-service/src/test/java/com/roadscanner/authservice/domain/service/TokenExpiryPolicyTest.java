package com.roadscanner.authservice.domain.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenExpiryPolicyTest {

    @Test
    void accessTokenExpiryIsComputedFromIssuedAt() {
        TokenExpiryPolicy policy = TokenExpiryPolicy.of(Duration.ofMinutes(15), Duration.ofDays(30));
        Instant issuedAt = Instant.parse("2026-07-14T10:00:00Z");

        assertThat(policy.accessTokenExpiry(issuedAt)).isEqualTo(issuedAt.plus(Duration.ofMinutes(15)));
        assertThat(policy.refreshTokenExpiry(issuedAt)).isEqualTo(issuedAt.plus(Duration.ofDays(30)));
    }

    @Test
    void rejectsAccessTtlNotShorterThanRefreshTtl() {
        assertThatThrownBy(() -> TokenExpiryPolicy.of(Duration.ofDays(30), Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly shorter");
    }

    @Test
    void rejectsAccessTtlLongerThanRefreshTtl() {
        assertThatThrownBy(() -> TokenExpiryPolicy.of(Duration.ofDays(31), Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTtls() {
        assertThatThrownBy(() -> TokenExpiryPolicy.of(Duration.ZERO, Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TokenExpiryPolicy.of(Duration.ofMinutes(15), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
