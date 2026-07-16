package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy;
import com.roadscanner.authservice.domain.service.TokenExpiryPolicy;
import com.roadscanner.authservice.testsupport.MutableClock;
import com.roadscanner.authservice.testsupport.fakes.InMemoryRefreshTokenRepository;
import com.roadscanner.authservice.testsupport.fakes.RecordingRevocationCache;
import com.roadscanner.authservice.testsupport.fakes.StubTokenGenerator;
import com.roadscanner.authservice.testsupport.fakes.StubTokenSigner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SessionRevokerTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    private final InMemoryRefreshTokenRepository refreshTokenRepository = new InMemoryRefreshTokenRepository();
    private final RecordingRevocationCache revocationCache = new RecordingRevocationCache();
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();
    private final MutableClock clock = new MutableClock(NOW);

    private final TokenIssuer tokenIssuer = new TokenIssuer(
            refreshTokenRepository, tokenGenerator, new StubTokenSigner(),
            TokenExpiryPolicy.of(Duration.ofMinutes(15), Duration.ofDays(14)), clock);

    private final SessionRevoker revoker = new SessionRevoker(
            new RevokeSessionService(refreshTokenRepository, revocationCache),
            new RevokeAllSessionsService(refreshTokenRepository, revocationCache, new RefreshTokenFamilyPolicy()),
            refreshTokenRepository, tokenGenerator, clock);

    private final UserId userId = UserId.generate();

    @Test
    void logoutRevokesExactlyThePresentedSession() {
        IssuedTokens sessionA = tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.of("phone"));
        IssuedTokens sessionB = tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.of("laptop"));

        revoker.logout(sessionA.refreshToken());

        assertThat(refreshTokenRepository.findByTokenHash(
                tokenGenerator.hashOf(sessionA.refreshToken())).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash(
                tokenGenerator.hashOf(sessionB.refreshToken())).orElseThrow().isRevoked()).isFalse();
        assertThat(revocationCache.isRevoked(tokenGenerator.hashOf(sessionA.refreshToken()))).isTrue();
    }

    @Test
    void logoutWithUnknownTokenIsASilentNoOp() {
        assertThatCode(() -> revoker.logout("never-issued")).doesNotThrowAnyException();
    }

    @Test
    void logoutIsIdempotent() {
        IssuedTokens session = tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.unknown());
        revoker.logout(session.refreshToken());
        assertThatCode(() -> revoker.logout(session.refreshToken())).doesNotThrowAnyException();
    }

    @Test
    void logoutAllRevokesEverySessionForTheUserOnly() {
        IssuedTokens mine1 = tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.unknown());
        IssuedTokens mine2 = tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.unknown());
        UserId otherUser = UserId.generate();
        IssuedTokens theirs = tokenIssuer.issue(otherUser, Role.TRAVELER, DeviceMetadata.unknown());

        revoker.logoutAll(userId);

        assertThat(refreshTokenRepository.findByUserId(userId))
                .allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(refreshTokenRepository.findByTokenHash(
                tokenGenerator.hashOf(theirs.refreshToken())).orElseThrow().isRevoked()).isFalse();
        assertThat(revocationCache.isRevoked(tokenGenerator.hashOf(mine1.refreshToken()))).isTrue();
        assertThat(revocationCache.isRevoked(tokenGenerator.hashOf(mine2.refreshToken()))).isTrue();
    }
}
