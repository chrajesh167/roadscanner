package com.roadscanner.authservice.domain.model;

import com.roadscanner.authservice.domain.exception.AccountLockedException;
import com.roadscanner.authservice.domain.exception.InvalidCredentialsException;
import com.roadscanner.authservice.domain.port.out.PasswordHasher;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The aggregate root for authentication — a user's ability to log in. Does not hold profile
 * data (name, contact, etc.); that's user-service's aggregate, keyed by the same
 * {@link UserId} but otherwise unrelated at the data level — see
 * docs/services/auth-service/responsibilities.md.
 *
 * {@link #authenticate} is the one entry point for the whole login business rule (allowed-state
 * check, password verification, attempt tracking, lockout) rather than several methods a caller
 * has to sequence correctly itself — see that method's Javadoc for why.
 */
public final class Credential {

    private final UserId userId;
    private final LoginIdentifier loginIdentifier;
    private PasswordHash passwordHash;
    private AccountStatus status;
    private int failedLoginAttempts;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    private Credential(UserId userId, LoginIdentifier loginIdentifier, PasswordHash passwordHash,
                        AccountStatus status, int failedLoginAttempts, Instant createdAt,
                        Instant updatedAt, Instant lastLoginAt) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.loginIdentifier = Objects.requireNonNull(loginIdentifier, "loginIdentifier must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("failedLoginAttempts must not be negative");
        }
        this.failedLoginAttempts = failedLoginAttempts;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.lastLoginAt = lastLoginAt;
    }

    /** Registers a brand-new identity. Always starts ACTIVE with zero failed attempts. */
    public static Credential register(UserId userId, LoginIdentifier loginIdentifier, PasswordHash passwordHash, Instant now) {
        return new Credential(userId, loginIdentifier, passwordHash, AccountStatus.ACTIVE, 0, now, now, null);
    }

    /** Rehydrates a Credential from persisted state. Trusts the state is already valid. */
    public static Credential reconstitute(UserId userId, LoginIdentifier loginIdentifier, PasswordHash passwordHash,
                                           AccountStatus status, int failedLoginAttempts, Instant createdAt,
                                           Instant updatedAt, Instant lastLoginAt) {
        return new Credential(userId, loginIdentifier, passwordHash, status, failedLoginAttempts, createdAt, updatedAt, lastLoginAt);
    }

    /**
     * The whole login business rule as one atomic aggregate operation: reject if the account
     * isn't {@link AccountStatus#ACTIVE}, verify the password via the given port, track the
     * outcome (reset attempts on success, increment and possibly lock on failure).
     *
     * Deliberately one method rather than exposing "isLoginAllowed()" as a separate public
     * check a caller queries first: that would let an implementation forget to call it, or call
     * it and then race against a concurrent state change before verifying the password. Folding
     * the whole sequence into one call is what makes this aggregate's invariant actually
     * enforced rather than merely advisory.
     *
     * {@code lockoutThreshold} is passed in, not hardcoded — it's an operational tuning knob
     * (docs/services/auth-service/security-design.md), and the domain layer stays
     * configuration-agnostic about its exact value while still enforcing that a threshold is
     * respected.
     *
     * @throws AccountLockedException if the account is LOCKED or SUSPENDED — thrown before the
     *         password is even checked, so a blocked account never leaks whether the presented
     *         password was otherwise correct.
     * @throws InvalidCredentialsException if the password does not match. Carries no identifying
     *         detail whatsoever — see that exception's Javadoc for why: this is what makes
     *         enumeration protection (docs/services/auth-service/api-contract.md) actually true
     *         at the domain level, not just a presentation-layer convention.
     */
    public void authenticate(String rawPassword, PasswordHasher passwordHasher, Instant now, int lockoutThreshold) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (status != AccountStatus.ACTIVE) {
            throw new AccountLockedException(userId);
        }
        if (!passwordHasher.matches(rawPassword, passwordHash)) {
            recordFailedLogin(now, lockoutThreshold);
            throw new InvalidCredentialsException();
        }
        recordSuccessfulLogin(now);
    }

    private void recordSuccessfulLogin(Instant now) {
        this.failedLoginAttempts = 0;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    private void recordFailedLogin(Instant now, int lockoutThreshold) {
        if (lockoutThreshold <= 0) {
            throw new IllegalArgumentException("lockoutThreshold must be positive");
        }
        this.failedLoginAttempts++;
        this.updatedAt = now;
        if (this.failedLoginAttempts >= lockoutThreshold) {
            this.status = AccountStatus.LOCKED;
        }
    }

    /**
     * Replaces the password hash — used for both a traveler-initiated password change and a
     * completed password-reset flow. Does not reset {@code failedLoginAttempts}; unrelated
     * concerns.
     */
    public void changePassword(PasswordHash newPasswordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * Lifts a system-imposed {@link AccountStatus#LOCKED} state. No-op if not currently LOCKED
     * — in particular, does nothing to a SUSPENDED account; that requires {@link #reinstate},
     * a distinct admin action, per {@link AccountStatus}'s Javadoc.
     */
    public void unlock(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == AccountStatus.LOCKED) {
            this.status = AccountStatus.ACTIVE;
            this.failedLoginAttempts = 0;
            this.updatedAt = now;
        }
    }

    /** Admin action. Always applies, regardless of current status. */
    public void suspend(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        this.status = AccountStatus.SUSPENDED;
        this.updatedAt = now;
    }

    /**
     * Lifts an admin-imposed {@link AccountStatus#SUSPENDED} state. No-op if not currently
     * SUSPENDED — in particular, does not clear a LOCKED state; that's {@link #unlock}'s job.
     */
    public void reinstate(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == AccountStatus.SUSPENDED) {
            this.status = AccountStatus.ACTIVE;
            this.failedLoginAttempts = 0;
            this.updatedAt = now;
        }
    }

    public UserId userId() {
        return userId;
    }

    public LoginIdentifier loginIdentifier() {
        return loginIdentifier;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<Instant> lastLoginAt() {
        return Optional.ofNullable(lastLoginAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Credential other)) return false;
        return userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }
}
