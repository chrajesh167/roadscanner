package com.roadscanner.authservice.domain.service;

import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.RefreshTokenId;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RefreshTokenFamilyPolicyTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-14T10:00:00Z");
    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    private final RefreshTokenFamilyPolicy policy = new RefreshTokenFamilyPolicy();

    private RefreshToken tokenFor(UserId userId) {
        return RefreshToken.issue(RefreshTokenId.generate(), new TokenHash(UUID.randomUUID().toString()),
                userId, ISSUED_AT, ISSUED_AT.plus(Duration.ofDays(30)), DeviceMetadata.unknown());
    }

    @Test
    void revokesEveryTokenInTheFamily() {
        List<RefreshToken> family = List.of(tokenFor(USER_ID), tokenFor(USER_ID), tokenFor(USER_ID));
        Instant revokeTime = ISSUED_AT.plusSeconds(30);

        policy.revokeFamily(family, revokeTime);

        assertThat(family).allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
    }

    @Test
    void isSafeWhenSomeTokensAreAlreadyRevoked() {
        RefreshToken alreadyRevoked = tokenFor(USER_ID);
        alreadyRevoked.revoke(ISSUED_AT.plusSeconds(10));
        List<RefreshToken> family = List.of(alreadyRevoked, tokenFor(USER_ID));

        assertThatCode(() -> policy.revokeFamily(family, ISSUED_AT.plusSeconds(30)))
                .doesNotThrowAnyException();
        assertThat(family).allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
    }
}
