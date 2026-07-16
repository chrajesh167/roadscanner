package com.roadscanner.authservice.application.usecase.passwordreset;

import com.roadscanner.authservice.domain.exception.ResetTokenInvalidException;
import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.ConfirmPasswordReset;
import com.roadscanner.authservice.domain.port.out.PasswordResetRepository;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * The confirm-reset flow the REST adapter calls with the presented raw token: resolves it to
 * the persisted {@link PasswordResetRequest} — the I/O {@link ConfirmPasswordReset}'s Javadoc
 * assigns to the application layer — then delegates to that port. An unknown token raises the
 * same {@link ResetTokenInvalidException} as a used or expired one; the client can never
 * distinguish which way a reset token was invalid.
 */
@Transactional
public class PasswordResetConfirmer {

    private final ConfirmPasswordReset confirmPasswordReset;
    private final PasswordResetRepository passwordResetRepository;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public PasswordResetConfirmer(ConfirmPasswordReset confirmPasswordReset,
                                  PasswordResetRepository passwordResetRepository,
                                  TokenGenerator tokenGenerator,
                                  Clock clock) {
        this.confirmPasswordReset = confirmPasswordReset;
        this.passwordResetRepository = passwordResetRepository;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    public UserId confirm(String rawResetToken, String newRawPassword) {
        PasswordResetRequest request = passwordResetRepository
                .findByTokenHash(tokenGenerator.hashOf(rawResetToken))
                .orElseThrow(() -> new ResetTokenInvalidException("Password reset token is invalid"));
        return confirmPasswordReset
                .confirm(new ConfirmPasswordReset.ConfirmPasswordResetCommand(
                        request, newRawPassword, Instant.now(clock)))
                .userId();
    }
}
