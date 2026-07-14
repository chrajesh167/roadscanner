package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.LoginIdentifier;

import java.util.Objects;

/**
 * Begins account recovery. Per docs/services/auth-service/api-contract.md's enumeration
 * protection contract, this must behave identically whether or not the identifier is
 * registered — including from this port's own return type, which is why
 * {@link RequestPasswordResetResult} is intentionally empty: there is no field this use case
 * could expose without risking a caller building an "identifier not found" branch on it.
 */
public interface RequestPasswordReset {

    RequestPasswordResetResult request(RequestPasswordResetCommand command);

    record RequestPasswordResetCommand(LoginIdentifier loginIdentifier) {
        public RequestPasswordResetCommand {
            Objects.requireNonNull(loginIdentifier, "loginIdentifier must not be null");
        }
    }

    /** Intentionally empty — see this interface's Javadoc. */
    record RequestPasswordResetResult() {
    }
}
