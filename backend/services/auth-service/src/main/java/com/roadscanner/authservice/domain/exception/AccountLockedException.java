package com.roadscanner.authservice.domain.exception;

import com.roadscanner.authservice.domain.model.UserId;

/**
 * Login was rejected because the account is not {@code ACTIVE} (either {@code LOCKED} or
 * {@code SUSPENDED} — see {@link com.roadscanner.authservice.domain.model.AccountStatus}).
 * Carries the {@link UserId} for server-side logging/audit; the client-facing message stays
 * generic regardless (mapped at {@code adapter.in.rest}, per
 * docs/services/auth-service/exception-strategy.md).
 */
public final class AccountLockedException extends AuthServiceException {

    private final UserId userId;

    public AccountLockedException(UserId userId) {
        super("Account is not active: " + userId);
        this.userId = userId;
    }

    public UserId userId() {
        return userId;
    }
}
