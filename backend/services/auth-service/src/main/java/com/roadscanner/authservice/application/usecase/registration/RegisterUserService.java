package com.roadscanner.authservice.application.usecase.registration;

import com.roadscanner.authservice.domain.exception.IdentifierAlreadyRegisteredException;
import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.PasswordHash;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.RegisterUser;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;
import com.roadscanner.authservice.domain.port.out.PasswordHasher;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;
import com.roadscanner.authservice.domain.service.PasswordComplexityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Implements {@link RegisterUser}: complexity check, uniqueness check, hash, persist, and the
 * default {@code TRAVELER} role assignment (docs/services/auth-service/api-contract.md:
 * "Defaults role to TRAVELER"). Token issuance is deliberately not here — the REST adapter
 * composes registration with {@code TokenIssuer} per {@link RegisterUser}'s Javadoc.
 *
 * The uniqueness check is advisory (two concurrent registrations can both pass it); the
 * database's unique constraint on login_identifier is the actual guarantee, surfaced to the
 * client by the global exception mapping.
 */
@Transactional
public class RegisterUserService implements RegisterUser {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserService.class);

    private final CredentialRepository credentialRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordComplexityPolicy passwordComplexityPolicy;
    private final Clock clock;

    public RegisterUserService(CredentialRepository credentialRepository,
                               RoleAssignmentRepository roleAssignmentRepository,
                               PasswordHasher passwordHasher,
                               PasswordComplexityPolicy passwordComplexityPolicy,
                               Clock clock) {
        this.credentialRepository = credentialRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.passwordHasher = passwordHasher;
        this.passwordComplexityPolicy = passwordComplexityPolicy;
        this.clock = clock;
    }

    @Override
    public RegistrationResult register(RegisterUserCommand command) {
        passwordComplexityPolicy.validate(command.rawPassword());
        if (credentialRepository.existsByLoginIdentifier(command.loginIdentifier())) {
            throw new IdentifierAlreadyRegisteredException(command.loginIdentifier());
        }

        Instant now = Instant.now(clock);
        PasswordHash passwordHash = passwordHasher.hash(command.rawPassword());
        Credential credential = Credential.register(UserId.generate(), command.loginIdentifier(), passwordHash, now);
        credentialRepository.save(credential);
        roleAssignmentRepository.save(
                RoleAssignment.assign(credential.userId(), Role.TRAVELER, AssignedBy.service("auth-service"), now));

        log.info("Registered new user {} with default role {}", credential.userId(), Role.TRAVELER);
        return new RegistrationResult(credential.userId());
    }
}
