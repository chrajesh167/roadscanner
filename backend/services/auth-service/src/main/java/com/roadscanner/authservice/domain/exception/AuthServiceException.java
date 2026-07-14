package com.roadscanner.authservice.domain.exception;

/**
 * Root of the auth-service exception hierarchy (docs/services/auth-service/exception-strategy.md).
 * Business-specific subtypes (InvalidCredentialsException, TokenExpiredException,
 * TokenReusedException, etc.) are added alongside the use cases they belong to — see
 * docs/services/auth-service/implementation-roadmap.md. Deliberately not created in this
 * bootstrap, since those use cases (login, JWT, password reset) are out of scope today.
 *
 * Framework-free by design: this is a domain type, not a Spring or HTTP type. Translation to an
 * HTTP response happens exclusively in {@link com.roadscanner.authservice.adapter.in.rest.exception
 * GlobalExceptionHandler}.
 */
public abstract class AuthServiceException extends RuntimeException {

    protected AuthServiceException(String message) {
        super(message);
    }

    protected AuthServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
