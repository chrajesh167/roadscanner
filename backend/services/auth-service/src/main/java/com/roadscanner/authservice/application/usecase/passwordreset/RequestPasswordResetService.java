package com.roadscanner.authservice.application.usecase.passwordreset;

import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.PasswordResetRequestId;
import com.roadscanner.authservice.domain.port.in.RequestPasswordReset;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;
import com.roadscanner.authservice.domain.port.out.PasswordResetRepository;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Implements {@link RequestPasswordReset}. Behaves identically whether or not the identifier
 * is registered — same (empty) result, no exception either way — per the enumeration
 * protection contract in docs/services/auth-service/api-contract.md.
 *
 * Delivery of the raw reset token is notification-service's job, not this service's
 * (docs/services/auth-service/responsibilities.md, "Notification Delivery") — and that service
 * does not exist yet, so the raw token is currently discarded after hashing. The reset request
 * is fully persisted and confirmable; wiring the raw value to a notification mechanism is the
 * one open integration point, tracked in this service's README. The raw token is never logged
 * (logging-observability.md's absolute redaction rule).
 */
@Transactional
public class RequestPasswordResetService implements RequestPasswordReset {

    private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetService.class);

    private final CredentialRepository credentialRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final TokenGenerator tokenGenerator;
    private final Duration resetTokenTtl;
    private final Clock clock;

    public RequestPasswordResetService(CredentialRepository credentialRepository,
                                       PasswordResetRepository passwordResetRepository,
                                       TokenGenerator tokenGenerator,
                                       Duration resetTokenTtl,
                                       Clock clock) {
        this.credentialRepository = credentialRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.tokenGenerator = tokenGenerator;
        this.resetTokenTtl = resetTokenTtl;
        this.clock = clock;
    }

    @Override
    public RequestPasswordResetResult request(RequestPasswordResetCommand command) {
        credentialRepository.findByLoginIdentifier(command.loginIdentifier()).ifPresent(credential -> {
            Instant now = Instant.now(clock);
            TokenGenerator.GeneratedToken generated = tokenGenerator.generate();
            PasswordResetRequest resetRequest = PasswordResetRequest.issue(
                    PasswordResetRequestId.generate(), generated.tokenHash(),
                    credential.userId(), now.plus(resetTokenTtl));
            passwordResetRepository.save(resetRequest);
            log.info("Password reset requested for user {} (request {})", credential.userId(), resetRequest.id());
        });
        return new RequestPasswordResetResult();
    }
}
