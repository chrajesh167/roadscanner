package com.roadscanner.providerintegrationservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Covers the idempotent state-transition invariants — matching {@code RefreshToken.revoke}'s
 * pattern in {@code auth-service} and {@code SearchableTrip.cancel}'s in {@code search-service}. */
class ProviderSessionTest {

    private static final Instant OPENED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final ProviderToken TOKEN = new ProviderToken("access", "refresh", "Bearer",
            OPENED_AT.plusSeconds(3600));

    private ProviderSession open() {
        return ProviderSession.open(ProviderSessionId.generate(), ProviderType.MOCK, TOKEN, OPENED_AT);
    }

    @Test
    void opensActiveWithGivenToken() {
        ProviderSession session = open();

        assertThat(session.isActive()).isTrue();
        assertThat(session.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.token()).isEqualTo(TOKEN);
    }

    @Test
    void expireIsIdempotent() {
        ProviderSession session = open();

        assertThat(session.expire(OPENED_AT.plusSeconds(10))).isTrue();
        assertThat(session.status()).isEqualTo(SessionStatus.EXPIRED);
        assertThat(session.expire(OPENED_AT.plusSeconds(20))).isFalse();
    }

    @Test
    void revokeIsIdempotentAndNeverUnexpires() {
        ProviderSession session = open();
        session.expire(OPENED_AT.plusSeconds(10));

        assertThat(session.revoke(OPENED_AT.plusSeconds(20))).isFalse();
        assertThat(session.status()).isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    void applyRefreshedTokenRejectsNonActiveSession() {
        ProviderSession session = open();
        session.revoke(OPENED_AT.plusSeconds(10));

        assertThatThrownBy(() -> session.applyRefreshedToken(TOKEN, OPENED_AT.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isTokenExpiredReflectsUnderlyingTokenExpiry() {
        ProviderSession session = open();

        assertThat(session.isTokenExpired(OPENED_AT)).isFalse();
        assertThat(session.isTokenExpired(TOKEN.expiresAt().plusSeconds(1))).isTrue();
    }
}
