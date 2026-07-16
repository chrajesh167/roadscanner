package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.exception.InvalidCredentialsException;
import com.roadscanner.authservice.domain.exception.TokenExpiredException;
import com.roadscanner.authservice.domain.exception.TokenReusedException;
import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy;
import com.roadscanner.authservice.domain.service.TokenExpiryPolicy;
import com.roadscanner.authservice.testsupport.MutableClock;
import com.roadscanner.authservice.testsupport.fakes.InMemoryRefreshTokenRepository;
import com.roadscanner.authservice.testsupport.fakes.InMemoryRoleAssignmentRepository;
import com.roadscanner.authservice.testsupport.fakes.RecordingRevocationCache;
import com.roadscanner.authservice.testsupport.fakes.StubTokenGenerator;
import com.roadscanner.authservice.testsupport.fakes.StubTokenSigner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the two rotation invariants testing-strategy.md calls out separately: a rotated token
 * cannot be reused (the basic invariant), and a reuse attempt revokes every session for the
 * user (the compromise response).
 */
class TokenRefresherTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final InMemoryRefreshTokenRepository refreshTokenRepository = new InMemoryRefreshTokenRepository();
    private final InMemoryRoleAssignmentRepository roleAssignmentRepository = new InMemoryRoleAssignmentRepository();
    private final RecordingRevocationCache revocationCache = new RecordingRevocationCache();
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();
    private final MutableClock clock = new MutableClock(NOW);
    private final TokenExpiryPolicy expiryPolicy = TokenExpiryPolicy.of(ACCESS_TTL, REFRESH_TTL);

    private final TokenIssuer tokenIssuer = new TokenIssuer(
            refreshTokenRepository, tokenGenerator, new StubTokenSigner(), expiryPolicy, clock);

    private final TokenRefresher refresher = new TokenRefresher(
            new RefreshAccessTokenService(refreshTokenRepository, revocationCache),
            refreshTokenRepository, roleAssignmentRepository, new RefreshTokenFamilyPolicy(),
            revocationCache, tokenGenerator, new StubTokenSigner(), expiryPolicy, clock);

    private final UserId userId = UserId.generate();

    private IssuedTokens startSession() {
        roleAssignmentRepository.save(RoleAssignment.assign(
                userId, Role.TRAVELER, AssignedBy.service("auth-service"), NOW));
        return tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.unknown());
    }

    @Test
    void rotatesTheRefreshTokenAndSignsANewAccessToken() {
        IssuedTokens session = startSession();

        IssuedTokens refreshed = refresher.refresh(session.refreshToken());

        assertThat(refreshed.refreshToken()).isNotEqualTo(session.refreshToken());
        assertThat(refreshed.userId()).isEqualTo(userId);
        assertThat(refreshed.role()).isEqualTo(Role.TRAVELER);

        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(tokenGenerator.hashOf(session.refreshToken())).orElseThrow();
        RefreshToken newToken = refreshTokenRepository
                .findByTokenHash(tokenGenerator.hashOf(refreshed.refreshToken())).orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(newToken.isActive(NOW)).isTrue();
        assertThat(newToken.replaces()).contains(oldToken.id());
        assertThat(revocationCache.isRevoked(oldToken.tokenHash())).isTrue();
    }

    @Test
    void rotatedTokenCannotBeUsedAgain() {
        IssuedTokens session = startSession();
        refresher.refresh(session.refreshToken());

        assertThatThrownBy(() -> refresher.refresh(session.refreshToken()))
                .isInstanceOf(TokenReusedException.class);
    }

    @Test
    void reuseDetectionRevokesEverySessionForTheUser() {
        IssuedTokens session = startSession();
        IssuedTokens otherDevice = tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.of("other-device"));
        IssuedTokens refreshed = refresher.refresh(session.refreshToken());

        assertThatThrownBy(() -> refresher.refresh(session.refreshToken()))
                .isInstanceOf(TokenReusedException.class);

        // Every token for the user — the rotated successor AND the unrelated device session —
        // is revoked, in the repository and in the cache.
        assertThat(refreshTokenRepository.findByUserId(userId))
                .allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(revocationCache.isRevoked(tokenGenerator.hashOf(refreshed.refreshToken()))).isTrue();
        assertThat(revocationCache.isRevoked(tokenGenerator.hashOf(otherDevice.refreshToken()))).isTrue();

        assertThatThrownBy(() -> refresher.refresh(refreshed.refreshToken()))
                .isInstanceOf(TokenReusedException.class);
        assertThatThrownBy(() -> refresher.refresh(otherDevice.refreshToken()))
                .isInstanceOf(TokenReusedException.class);
    }

    @Test
    void unknownTokenFailsExactlyLikeAnyOtherBadCredential() {
        assertThatThrownBy(() -> refresher.refresh("never-issued-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void expiredTokenIsRejectedWithoutRevokingAnything() {
        IssuedTokens session = startSession();
        clock.advanceBy(REFRESH_TTL.plusSeconds(1));

        assertThatThrownBy(() -> refresher.refresh(session.refreshToken()))
                .isInstanceOf(TokenExpiredException.class);
        assertThat(refreshTokenRepository
                .findByTokenHash(tokenGenerator.hashOf(session.refreshToken())).orElseThrow().isRevoked())
                .isFalse();
    }
}
