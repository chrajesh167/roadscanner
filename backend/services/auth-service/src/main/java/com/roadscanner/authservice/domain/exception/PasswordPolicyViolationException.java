package com.roadscanner.authservice.domain.exception;

/**
 * A raw password fails the platform's complexity policy. Thrown by
 * {@code PasswordComplexityPolicy.validate}. Safe to reveal which rule failed to the client —
 * unlike credential/token failures, this carries no enumeration risk (see
 * docs/services/auth-service/validation-strategy.md).
 */
public final class PasswordPolicyViolationException extends AuthServiceException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
