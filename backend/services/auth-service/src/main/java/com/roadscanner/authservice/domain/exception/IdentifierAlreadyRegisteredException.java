package com.roadscanner.authservice.domain.exception;

import com.roadscanner.authservice.domain.model.LoginIdentifier;

/**
 * Registration was attempted with a login identifier that's already taken. Thrown by the
 * application-layer implementation of {@code RegisterUser} (next implementation phase) after a
 * {@code CredentialRepository} uniqueness check — not by domain model code itself, since
 * checking uniqueness across all credentials requires repository access.
 *
 * Deliberately the one exception that <em>does</em> carry the identifier — per
 * docs/services/auth-service/api-contract.md, revealing "this identifier is already registered"
 * to a prospective new user is the intentional, single exception to enumeration protection
 * elsewhere in this service.
 */
public final class IdentifierAlreadyRegisteredException extends AuthServiceException {

    private final LoginIdentifier loginIdentifier;

    public IdentifierAlreadyRegisteredException(LoginIdentifier loginIdentifier) {
        super("Identifier already registered: " + loginIdentifier);
        this.loginIdentifier = loginIdentifier;
    }

    public LoginIdentifier loginIdentifier() {
        return loginIdentifier;
    }
}
