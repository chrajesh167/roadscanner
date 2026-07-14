package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;

import java.util.Objects;

/**
 * Verifies credentials — "Login". The implementation (next phase) fetches the
 * {@link com.roadscanner.authservice.domain.model.Credential} by identifier and delegates the
 * whole business rule to {@code Credential.authenticate(...)}; this port only declares the
 * use-case boundary.
 *
 * Returns proof of identity and role, not a session or tokens — starting a session (issuing a
 * {@link com.roadscanner.authservice.domain.model.RefreshToken}) is a related but separate
 * concern the application layer composes on top of a successful authentication, using
 * {@code RefreshToken.issue(...)} directly, per the same reasoning as {@link RegisterUser}.
 */
public interface AuthenticateUser {

    AuthenticationResult authenticate(AuthenticateUserCommand command);

    record AuthenticateUserCommand(LoginIdentifier loginIdentifier, String rawPassword) {
        public AuthenticateUserCommand {
            Objects.requireNonNull(loginIdentifier, "loginIdentifier must not be null");
            Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        }
    }

    record AuthenticationResult(UserId userId, Role role) {
        public AuthenticationResult {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(role, "role must not be null");
        }
    }
}
