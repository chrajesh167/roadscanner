package com.roadscanner.authservice.domain.model;

import com.roadscanner.authservice.domain.exception.ResetTokenInvalidException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetRequestTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-14T10:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofHours(1));

    private PasswordResetRequest freshRequest() {
        return PasswordResetRequest.issue(PasswordResetRequestId.generate(), new TokenHash("hash"),
                new UserId(UUID.randomUUID()), EXPIRES_AT);
    }

    @Test
    void issuedRequestIsNotUsed() {
        assertThat(freshRequest().isUsed()).isFalse();
    }

    @Test
    void useSucceedsOnceBeforeExpiry() {
        PasswordResetRequest request = freshRequest();
        Instant useTime = ISSUED_AT.plusSeconds(60);

        request.use(useTime);

        assertThat(request.isUsed()).isTrue();
        assertThat(request.usedAt()).contains(useTime);
    }

    @Test
    void usingItTwiceThrowsResetTokenInvalid() {
        PasswordResetRequest request = freshRequest();
        request.use(ISSUED_AT.plusSeconds(60));

        assertThatThrownBy(() -> request.use(ISSUED_AT.plusSeconds(120)))
                .isInstanceOf(ResetTokenInvalidException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void usingItAfterExpiryThrowsResetTokenInvalid() {
        PasswordResetRequest request = freshRequest();

        assertThatThrownBy(() -> request.use(EXPIRES_AT.plusSeconds(1)))
                .isInstanceOf(ResetTokenInvalidException.class)
                .hasMessageContaining("expired");

        assertThat(request.isUsed()).isFalse();
    }

    @Test
    void equalityIsByIdOnly() {
        PasswordResetRequestId id = PasswordResetRequestId.generate();
        UserId userId = new UserId(UUID.randomUUID());
        PasswordResetRequest first = PasswordResetRequest.issue(id, new TokenHash("hash-1"), userId, EXPIRES_AT);
        PasswordResetRequest second = PasswordResetRequest.reconstitute(id, new TokenHash("hash-2"), userId,
                EXPIRES_AT, ISSUED_AT.plusSeconds(30));

        assertThat(first).isEqualTo(second);
    }
}
