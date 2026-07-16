package com.roadscanner.authservice.application.usecase.login;

import com.roadscanner.authservice.domain.exception.InvalidCredentialsException;
import com.roadscanner.authservice.domain.model.AccountStatus;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.port.in.AuthenticateUser;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;
import com.roadscanner.authservice.domain.port.out.PasswordHasher;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;
import com.roadscanner.authservice.domain.service.PasswordHashingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Implements {@link AuthenticateUser}. Orchestration only — the login business rule itself
 * (status check, password verification, attempt tracking, lockout) is
 * {@code Credential.authenticate(...)}'s job, per that method's Javadoc.
 *
 * Three orchestration concerns live here because they need I/O the aggregate can't perform:
 * <ul>
 *   <li><b>Lockout expiry.</b> A LOCKED account whose lockout window has elapsed is unlocked
 *       before authenticating — this is what makes the lockout "temporary" per
 *       docs/services/auth-service/security-design.md.</li>
 *   <li><b>Failed-attempt persistence.</b> A failed attempt mutates the aggregate (counter,
 *       possible lock) and then throws; the mutation must still be saved, which is why the
 *       transaction is marked {@code noRollbackFor} the failure exception.</li>
 *   <li><b>Rehash on login.</b> The one moment the raw password is legitimately in hand — if
 *       the stored hash predates the current algorithm baseline, it is upgraded here
 *       (docs/services/auth-service/security-design.md: cost factor "expected to increase
 *       over time").</li>
 * </ul>
 */
@Transactional(noRollbackFor = InvalidCredentialsException.class)
public class AuthenticateUserService implements AuthenticateUser {

    private static final Logger log = LoggerFactory.getLogger(AuthenticateUserService.class);

    private final CredentialRepository credentialRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordHashingPolicy passwordHashingPolicy;
    private final int lockoutThreshold;
    private final Duration lockoutDuration;
    private final Clock clock;

    public AuthenticateUserService(CredentialRepository credentialRepository,
                                   RoleAssignmentRepository roleAssignmentRepository,
                                   PasswordHasher passwordHasher,
                                   PasswordHashingPolicy passwordHashingPolicy,
                                   int lockoutThreshold,
                                   Duration lockoutDuration,
                                   Clock clock) {
        this.credentialRepository = credentialRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.passwordHasher = passwordHasher;
        this.passwordHashingPolicy = passwordHashingPolicy;
        this.lockoutThreshold = lockoutThreshold;
        this.lockoutDuration = lockoutDuration;
        this.clock = clock;
    }

    @Override
    public AuthenticationResult authenticate(AuthenticateUserCommand command) {
        Instant now = Instant.now(clock);
        // Same exception for "unknown identifier" as for "wrong password" — enumeration
        // protection, enforced here and not just at the presentation layer.
        Credential credential = credentialRepository.findByLoginIdentifier(command.loginIdentifier())
                .orElseThrow(InvalidCredentialsException::new);

        unlockIfLockoutExpired(credential, now);

        try {
            credential.authenticate(command.rawPassword(), passwordHasher, now, lockoutThreshold);
        } catch (InvalidCredentialsException e) {
            credentialRepository.save(credential);
            if (credential.status() == AccountStatus.LOCKED) {
                log.warn("Account locked after {} failed login attempts: {}",
                        credential.failedLoginAttempts(), credential.userId());
            } else {
                log.info("Failed login attempt {} for user {}",
                        credential.failedLoginAttempts(), credential.userId());
            }
            throw e;
        }

        if (passwordHashingPolicy.needsRehash(credential.passwordHash())) {
            credential.changePassword(passwordHasher.hash(command.rawPassword()), now);
            log.info("Upgraded password hash to current baseline for user {}", credential.userId());
        }
        credentialRepository.save(credential);

        Role role = currentRoleOf(credential);
        log.info("Successful login for user {} with role {}", credential.userId(), role);
        return new AuthenticationResult(credential.userId(), role);
    }

    private void unlockIfLockoutExpired(Credential credential, Instant now) {
        if (credential.status() == AccountStatus.LOCKED
                && !now.isBefore(credential.updatedAt().plus(lockoutDuration))) {
            credential.unlock(now);
            log.info("Lockout window elapsed — unlocked account for user {}", credential.userId());
        }
    }

    private Role currentRoleOf(Credential credential) {
        return roleAssignmentRepository.findLatestByUserId(credential.userId())
                .map(RoleAssignment::role)
                .orElseGet(() -> {
                    // Registration always writes an assignment, so this indicates inconsistent
                    // data; least-privilege default rather than refusing login outright.
                    log.warn("No role assignment found for user {} — defaulting to {}",
                            credential.userId(), Role.TRAVELER);
                    return Role.TRAVELER;
                });
    }
}
