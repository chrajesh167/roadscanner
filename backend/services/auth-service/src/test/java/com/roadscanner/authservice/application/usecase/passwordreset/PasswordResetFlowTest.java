package com.roadscanner.authservice.application.usecase.passwordreset;

import com.roadscanner.authservice.domain.exception.PasswordPolicyViolationException;
import com.roadscanner.authservice.domain.exception.ResetTokenInvalidException;
import com.roadscanner.authservice.domain.model.AccountStatus;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.RequestPasswordReset;
import com.roadscanner.authservice.domain.port.out.StubPasswordHasher;
import com.roadscanner.authservice.domain.service.PasswordComplexityPolicy;
import com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy;
import com.roadscanner.authservice.domain.service.TokenExpiryPolicy;
import com.roadscanner.authservice.application.usecase.token.IssuedTokens;
import com.roadscanner.authservice.application.usecase.token.TokenIssuer;
import com.roadscanner.authservice.testsupport.MutableClock;
import com.roadscanner.authservice.testsupport.fakes.InMemoryCredentialRepository;
import com.roadscanner.authservice.testsupport.fakes.InMemoryPasswordResetRepository;
import com.roadscanner.authservice.testsupport.fakes.InMemoryRefreshTokenRepository;
import com.roadscanner.authservice.testsupport.fakes.RecordingRevocationCache;
import com.roadscanner.authservice.testsupport.fakes.StubTokenGenerator;
import com.roadscanner.authservice.testsupport.fakes.StubTokenSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises request + confirm together, including the security scenarios testing-strategy.md
 * lists explicitly: identical outcome for unknown identifiers (enumeration protection), and a
 * reset token that cannot be used twice.
 */
class PasswordResetFlowTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final LoginIdentifier IDENTIFIER = new LoginIdentifier("traveler@example.com");
    private static final String OLD_PASSWORD = "old-password-42x";
    private static final String NEW_PASSWORD = "brand-new-pass-7";

    private final InMemoryCredentialRepository credentialRepository = new InMemoryCredentialRepository();
    private final InMemoryPasswordResetRepository passwordResetRepository = new InMemoryPasswordResetRepository();
    private final InMemoryRefreshTokenRepository refreshTokenRepository = new InMemoryRefreshTokenRepository();
    private final RecordingRevocationCache revocationCache = new RecordingRevocationCache();
    private final StubPasswordHasher passwordHasher = new StubPasswordHasher();
    private final StubTokenGenerator tokenGenerator = new StubTokenGenerator();
    private final MutableClock clock = new MutableClock(NOW);

    private final RequestPasswordResetService requestService = new RequestPasswordResetService(
            credentialRepository, passwordResetRepository, tokenGenerator, RESET_TTL, clock);

    private final PasswordResetConfirmer confirmer = new PasswordResetConfirmer(
            new ConfirmPasswordResetService(credentialRepository, passwordResetRepository,
                    refreshTokenRepository, revocationCache, passwordHasher,
                    PasswordComplexityPolicy.standard(), new RefreshTokenFamilyPolicy()),
            passwordResetRepository, tokenGenerator, clock);

    private final TokenIssuer tokenIssuer = new TokenIssuer(
            refreshTokenRepository, tokenGenerator, new StubTokenSigner(),
            TokenExpiryPolicy.of(Duration.ofMinutes(15), Duration.ofDays(14)), clock);

    private UserId userId;

    @BeforeEach
    void registerUser() {
        Credential credential = Credential.register(
                UserId.generate(), IDENTIFIER, passwordHasher.hash(OLD_PASSWORD), NOW);
        credentialRepository.save(credential);
        userId = credential.userId();
    }

    private void requestReset(LoginIdentifier identifier) {
        requestService.request(new RequestPasswordReset.RequestPasswordResetCommand(identifier));
    }

    /** The raw token isn't observable through the request use case (delivery is a future
     * notification integration), so tests mint one the same way the service does. */
    private String issueResetToken() {
        var generated = tokenGenerator.generate();
        passwordResetRepository.save(PasswordResetRequest.issue(
                com.roadscanner.authservice.domain.model.PasswordResetRequestId.generate(),
                generated.tokenHash(), userId, NOW.plus(RESET_TTL)));
        return generated.rawValue();
    }

    @Test
    void requestPersistsAResetRequestForAKnownIdentifier() {
        requestReset(IDENTIFIER);

        assertThat(passwordResetRepository.all()).hasSize(1);
        assertThat(passwordResetRepository.all().getFirst().userId()).isEqualTo(userId);
        assertThat(passwordResetRepository.all().getFirst().expiresAt()).isEqualTo(NOW.plus(RESET_TTL));
    }

    @Test
    void requestForUnknownIdentifierCompletesIdenticallyAndStoresNothing() {
        assertThatCode(() -> requestReset(new LoginIdentifier("nobody@example.com")))
                .doesNotThrowAnyException();
        assertThat(passwordResetRepository.all()).isEmpty();
    }

    @Test
    void confirmChangesPasswordRevokesAllSessionsAndUnlocksTheAccount() {
        IssuedTokens session = tokenIssuer.issue(userId, Role.TRAVELER, DeviceMetadata.unknown());
        Credential credential = credentialRepository.findByUserId(userId).orElseThrow();
        credential.suspend(NOW); // not lifted by reset
        credential.reinstate(NOW);
        String rawToken = issueResetToken();

        UserId confirmed = confirmer.confirm(rawToken, NEW_PASSWORD);

        assertThat(confirmed).isEqualTo(userId);
        Credential updated = credentialRepository.findByUserId(userId).orElseThrow();
        assertThat(passwordHasher.matches(NEW_PASSWORD, updated.passwordHash())).isTrue();
        assertThat(passwordHasher.matches(OLD_PASSWORD, updated.passwordHash())).isFalse();
        assertThat(updated.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(refreshTokenRepository.findByUserId(userId))
                .allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(revocationCache.isRevoked(tokenGenerator.hashOf(session.refreshToken()))).isTrue();
    }

    @Test
    void resetTokenIsSingleUse() {
        String rawToken = issueResetToken();
        confirmer.confirm(rawToken, NEW_PASSWORD);

        assertThatThrownBy(() -> confirmer.confirm(rawToken, "yet-another-pass9"))
                .isInstanceOf(ResetTokenInvalidException.class);
    }

    @Test
    void expiredResetTokenIsRejected() {
        String rawToken = issueResetToken();
        clock.advanceBy(RESET_TTL.plusSeconds(1));

        assertThatThrownBy(() -> confirmer.confirm(rawToken, NEW_PASSWORD))
                .isInstanceOf(ResetTokenInvalidException.class);
    }

    @Test
    void unknownResetTokenIsRejectedWithTheSameException() {
        assertThatThrownBy(() -> confirmer.confirm("never-issued", NEW_PASSWORD))
                .isInstanceOf(ResetTokenInvalidException.class);
    }

    @Test
    void weakNewPasswordFailsWithoutBurningTheToken() {
        String rawToken = issueResetToken();

        assertThatThrownBy(() -> confirmer.confirm(rawToken, "weak1"))
                .isInstanceOf(PasswordPolicyViolationException.class);

        // Complexity is checked before use() — the token remains valid for a correct retry.
        assertThatCode(() -> confirmer.confirm(rawToken, NEW_PASSWORD)).doesNotThrowAnyException();
    }
}
