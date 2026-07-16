package com.roadscanner.authservice.domain.exception;

import com.roadscanner.authservice.domain.model.UserId;

/**
 * An operation referenced a {@link UserId} that has no Credential — thrown by the AssignRole
 * use case when the target user does not exist. Only used on internal, admin/service-facing
 * operations (docs/services/auth-service/api-contract.md's Assign Role); client-facing
 * operations never throw this, since revealing user existence there would violate the
 * enumeration-protection rule.
 */
public final class UserNotFoundException extends AuthServiceException {

    private final UserId userId;

    public UserNotFoundException(UserId userId) {
        super("User not found: " + userId);
        this.userId = userId;
    }

    public UserId userId() {
        return userId;
    }
}
