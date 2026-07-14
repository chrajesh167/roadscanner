package com.roadscanner.authservice.domain.exception;

/**
 * Login failed — either the identifier doesn't exist or the password is wrong. Deliberately
 * carries no identifying context whatsoever, not even as a field only logs would see: per
 * docs/services/auth-service/security-design.md and api-contract.md, the platform must never
 * distinguish "unknown identifier" from "wrong password" anywhere, including internally. Giving
 * this exception a field to carry that distinction would make the leak one careless log
 * statement away; not having the field at all makes the leak impossible instead of merely
 * discouraged.
 */
public final class InvalidCredentialsException extends AuthServiceException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
