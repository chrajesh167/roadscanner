package com.roadscanner.authservice.domain.exception;

/** A password-reset token is unknown, already used, or expired. Thrown by {@code PasswordResetRequest.use}. */
public final class ResetTokenInvalidException extends AuthServiceException {

    public ResetTokenInvalidException(String message) {
        super(message);
    }
}
