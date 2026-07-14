package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.UserId;

import java.util.Objects;

/**
 * Creates a new identity — per docs/services/auth-service/responsibilities.md, this is the
 * entire scope of registration: it does not create a profile (user-service's job, via the
 * client-orchestrated two-call flow described there) and does not itself issue tokens (a
 * separate, composable concern — see docs/services/auth-service/api-contract.md's note that
 * bundling register+login into one HTTP response is an adapter/application-layer composition,
 * not a domain one).
 *
 * The command carries the raw password, not a {@link com.roadscanner.authservice.domain.model.PasswordHash}
 * — hashing happens inside the use-case implementation via
 * {@link com.roadscanner.authservice.domain.port.out.PasswordHasher}, after
 * {@link com.roadscanner.authservice.domain.service.PasswordComplexityPolicy} validates it.
 */
public interface RegisterUser {

    RegistrationResult register(RegisterUserCommand command);

    record RegisterUserCommand(LoginIdentifier loginIdentifier, String rawPassword) {
        public RegisterUserCommand {
            Objects.requireNonNull(loginIdentifier, "loginIdentifier must not be null");
            Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        }
    }

    record RegistrationResult(UserId userId) {
        public RegistrationResult {
            Objects.requireNonNull(userId, "userId must not be null");
        }
    }
}
