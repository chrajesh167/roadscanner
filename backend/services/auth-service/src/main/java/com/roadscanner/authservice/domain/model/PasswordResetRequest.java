package com.roadscanner.authservice.domain.model;

import com.roadscanner.authservice.domain.exception.ResetTokenInvalidException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A single password-reset attempt. {@link #use} enforces single-use and expiry as one atomic
 * aggregate invariant, directly implementing the behavioral contract in
 * docs/services/auth-service/api-contract.md ("Confirm Password Reset is single-use").
 */
public final class PasswordResetRequest {

    private final PasswordResetRequestId id;
    private final TokenHash tokenHash;
    private final UserId userId;
    private final Instant expiresAt;
    private Instant usedAt;

    private PasswordResetRequest(PasswordResetRequestId id, TokenHash tokenHash, UserId userId,
                                  Instant expiresAt, Instant usedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.usedAt = usedAt;
    }

    public static PasswordResetRequest issue(PasswordResetRequestId id, TokenHash tokenHash, UserId userId, Instant expiresAt) {
        return new PasswordResetRequest(id, tokenHash, userId, expiresAt, null);
    }

    /** Rehydrates a PasswordResetRequest from persisted state. Trusts the state is already valid. */
    public static PasswordResetRequest reconstitute(PasswordResetRequestId id, TokenHash tokenHash, UserId userId,
                                                      Instant expiresAt, Instant usedAt) {
        return new PasswordResetRequest(id, tokenHash, userId, expiresAt, usedAt);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * Consumes this reset request. Unlike {@link RefreshToken#revoke}, this is deliberately
     * <em>not</em> idempotent — a second use attempt is a genuine client-visible failure
     * (api-contract.md's "single-use" contract), not a background cleanup event that should be
     * silently tolerated, so it throws rather than no-op'ing.
     *
     * @throws ResetTokenInvalidException if already used or expired.
     */
    public void use(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (isUsed()) {
            throw new ResetTokenInvalidException("Password reset token has already been used");
        }
        if (isExpired(now)) {
            throw new ResetTokenInvalidException("Password reset token has expired");
        }
        this.usedAt = now;
    }

    public PasswordResetRequestId id() {
        return id;
    }

    public TokenHash tokenHash() {
        return tokenHash;
    }

    public UserId userId() {
        return userId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> usedAt() {
        return Optional.ofNullable(usedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordResetRequest other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
