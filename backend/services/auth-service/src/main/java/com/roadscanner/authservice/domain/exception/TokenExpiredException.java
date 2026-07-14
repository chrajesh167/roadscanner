package com.roadscanner.authservice.domain.exception;

/** A refresh token's validity window has passed. Thrown by {@code RefreshToken.rotate}. */
public final class TokenExpiredException extends AuthServiceException {

    public TokenExpiredException(String message) {
        super(message);
    }
}
