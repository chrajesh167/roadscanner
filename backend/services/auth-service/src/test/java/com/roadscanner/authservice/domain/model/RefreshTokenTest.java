package com.roadscanner.authservice.domain.model;

import com.roadscanner.authservice.domain.exception.TokenExpiredException;
import com.roadscanner.authservice.domain.exception.TokenReusedException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-14T10:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofDays(30));
    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    private RefreshToken freshToken() {
        return RefreshToken.issue(RefreshTokenId.generate(), new TokenHash("hash-1"), USER_ID,
                ISSUED_AT, EXPIRES_AT, DeviceMetadata.unknown());
    }

    @Test
    void issueRejectsExpiryNotAfterIssuedAt() {
        assertThatThrownBy(() -> RefreshToken.issue(RefreshTokenId.generate(), new TokenHash("h"), USER_ID,
                ISSUED_AT, ISSUED_AT, DeviceMetadata.unknown()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isActiveImmediatelyAfterIssue() {
        RefreshToken token = freshToken();
        assertThat(token.isActive(ISSUED_AT.plusSeconds(1))).isTrue();
    }

    @Test
    void isNotActiveOnceExpired() {
        RefreshToken token = freshToken();
        assertThat(token.isActive(EXPIRES_AT.plusSeconds(1))).isFalse();
        assertThat(token.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
    }

    @Test
    void isNotActiveOnceRevoked() {
        RefreshToken token = freshToken();
        token.revoke(ISSUED_AT.plusSeconds(1));
        assertThat(token.isActive(ISSUED_AT.plusSeconds(2))).isFalse();
    }

    @Test
    void revokeIsIdempotentAndKeepsTheFirstTimestamp() {
        RefreshToken token = freshToken();
        Instant firstRevocation = ISSUED_AT.plusSeconds(10);

        token.revoke(firstRevocation);
        token.revoke(ISSUED_AT.plusSeconds(20)); // second call must not overwrite

        assertThat(token.revokedAt()).contains(firstRevocation);
    }

    @Test
    void rotateReturnsANewTokenLinkedToThePredecessor() {
        RefreshToken original = freshToken();
        RefreshTokenId newId = RefreshTokenId.generate();
        Instant rotationTime = ISSUED_AT.plusSeconds(60);
        Instant newExpiry = rotationTime.plus(Duration.ofDays(30));

        RefreshToken rotated = original.rotate(newId, new TokenHash("hash-2"), rotationTime, newExpiry);

        assertThat(rotated.id()).isEqualTo(newId);
        assertThat(rotated.replaces()).contains(original.id());
        assertThat(rotated.userId()).isEqualTo(original.userId());
        assertThat(rotated.deviceMetadata()).isEqualTo(original.deviceMetadata());
        assertThat(rotated.isRevoked()).isFalse();
    }

    @Test
    void rotateRevokesTheOriginalToken() {
        RefreshToken original = freshToken();
        original.rotate(RefreshTokenId.generate(), new TokenHash("hash-2"), ISSUED_AT.plusSeconds(60), EXPIRES_AT.plusSeconds(60));

        assertThat(original.isRevoked()).isTrue();
    }

    @Test
    void rotatingAnAlreadyRevokedTokenThrowsTokenReused() {
        RefreshToken original = freshToken();
        original.revoke(ISSUED_AT.plusSeconds(10));

        assertThatThrownBy(() -> original.rotate(RefreshTokenId.generate(), new TokenHash("hash-2"),
                ISSUED_AT.plusSeconds(20), EXPIRES_AT.plusSeconds(20)))
                .isInstanceOf(TokenReusedException.class);
    }

    @Test
    void rotatingTwiceOnTheSameTokenIsReuseDetectionOnTheSecondAttempt() {
        // Simulates the exact attack scenario: legitimate rotation succeeds once, then the
        // same (now-superseded) token is presented again.
        RefreshToken original = freshToken();
        original.rotate(RefreshTokenId.generate(), new TokenHash("hash-2"), ISSUED_AT.plusSeconds(60), EXPIRES_AT.plusSeconds(60));

        assertThatThrownBy(() -> original.rotate(RefreshTokenId.generate(), new TokenHash("hash-3"),
                ISSUED_AT.plusSeconds(120), EXPIRES_AT.plusSeconds(120)))
                .isInstanceOf(TokenReusedException.class);
    }

    @Test
    void rotatingAnExpiredTokenThrowsTokenExpired() {
        RefreshToken original = freshToken();

        assertThatThrownBy(() -> original.rotate(RefreshTokenId.generate(), new TokenHash("hash-2"),
                EXPIRES_AT.plusSeconds(1), EXPIRES_AT.plusSeconds(60)))
                .isInstanceOf(TokenExpiredException.class);

        // An expired-but-not-yet-rotated token must not be silently revoked by a failed attempt.
        assertThat(original.isRevoked()).isFalse();
    }

    @Test
    void equalityIsByIdOnly() {
        RefreshTokenId id = RefreshTokenId.generate();
        RefreshToken first = RefreshToken.issue(id, new TokenHash("hash-1"), USER_ID, ISSUED_AT, EXPIRES_AT, DeviceMetadata.unknown());
        RefreshToken second = RefreshToken.reconstitute(id, new TokenHash("hash-2"), USER_ID, ISSUED_AT, EXPIRES_AT,
                null, null, DeviceMetadata.of("different device"));

        assertThat(first).isEqualTo(second);
    }
}
