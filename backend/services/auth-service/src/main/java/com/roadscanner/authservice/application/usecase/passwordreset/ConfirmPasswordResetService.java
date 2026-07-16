package com.roadscanner.authservice.application.usecase.passwordreset;

import com.roadscanner.authservice.domain.exception.ResetTokenInvalidException;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.port.in.ConfirmPasswordReset;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;
import com.roadscanner.authservice.domain.port.out.PasswordHasher;
import com.roadscanner.authservice.domain.port.out.PasswordResetRepository;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.RevocationCache;
import com.roadscanner.authservice.domain.service.PasswordComplexityPolicy;
import com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Implements {@link ConfirmPasswordReset}: complexity validation first (so a policy failure
 * doesn't burn the single-use token), then {@code PasswordResetRequest.use(...)} for the
 * single-use/expiry invariant, then the password change — with two security-driven side
 * effects the docs imply but the aggregate can't do itself:
 * <ul>
 *   <li><b>Every session is revoked.</b> Password reset is the account-recovery path — its
 *       premise is that the old credential may be compromised, and a stolen refresh token
 *       must not outlive the password it was obtained under.</li>
 *   <li><b>A brute-force lock is lifted.</b> Proving control of the reset token is a stronger
 *       signal than the lockout window expiring, so the reset also unlocks a LOCKED account
 *       (it does not lift an admin-imposed SUSPENDED state — {@code unlock} is a no-op there).</li>
 * </ul>
 */
public class ConfirmPasswordResetService implements ConfirmPasswordReset {

    private static final Logger log = LoggerFactory.getLogger(ConfirmPasswordResetService.class);

    private final CredentialRepository credentialRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevocationCache revocationCache;
    private final PasswordHasher passwordHasher;
    private final PasswordComplexityPolicy passwordComplexityPolicy;
    private final RefreshTokenFamilyPolicy refreshTokenFamilyPolicy;

    public ConfirmPasswordResetService(CredentialRepository credentialRepository,
                                       PasswordResetRepository passwordResetRepository,
                                       RefreshTokenRepository refreshTokenRepository,
                                       RevocationCache revocationCache,
                                       PasswordHasher passwordHasher,
                                       PasswordComplexityPolicy passwordComplexityPolicy,
                                       RefreshTokenFamilyPolicy refreshTokenFamilyPolicy) {
        this.credentialRepository = credentialRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.revocationCache = revocationCache;
        this.passwordHasher = passwordHasher;
        this.passwordComplexityPolicy = passwordComplexityPolicy;
        this.refreshTokenFamilyPolicy = refreshTokenFamilyPolicy;
    }

    @Override
    public ConfirmPasswordResetResult confirm(ConfirmPasswordResetCommand command) {
        passwordComplexityPolicy.validate(command.newRawPassword());

        PasswordResetRequest request = command.request();
        request.use(command.now());
        passwordResetRepository.save(request);

        Credential credential = credentialRepository.findByUserId(request.userId())
                .orElseThrow(() -> new ResetTokenInvalidException("Password reset token is no longer valid"));
        credential.changePassword(passwordHasher.hash(command.newRawPassword()), command.now());
        credential.unlock(command.now());
        credentialRepository.save(credential);

        revokeAllSessions(credential, command.now());
        log.info("Password reset completed for user {} (request {})", credential.userId(), request.id());
        return new ConfirmPasswordResetResult(credential.userId());
    }

    private void revokeAllSessions(Credential credential, Instant now) {
        List<RefreshToken> sessions = refreshTokenRepository.findByUserId(credential.userId());
        refreshTokenFamilyPolicy.revokeFamily(sessions, now);
        sessions.forEach(session -> {
            refreshTokenRepository.save(session);
            revocationCache.markRevoked(session.tokenHash(), session.expiresAt());
        });
    }
}
