package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A live authenticated session against one provider — the thing every other operation
 * (search, seat map, block, confirm, ticket) is performed through. Owns exactly two state
 * transitions, both idempotent no-ops on redelivery/repeat calls, matching
 * {@code auth-service}'s {@code RefreshToken.revoke}/{@code search-service}'s
 * {@code SearchableTrip.cancel} pattern: once {@link SessionStatus#REVOKED} or
 * {@link SessionStatus#EXPIRED}, a session never returns to {@link SessionStatus#ACTIVE} —
 * a new one is authenticated instead.
 */
public final class ProviderSession {

    private final ProviderSessionId id;
    private final ProviderType providerType;
    private SessionStatus status;
    private ProviderToken token;
    private final Instant createdAt;
    private Instant updatedAt;

    private ProviderSession(ProviderSessionId id, ProviderType providerType, SessionStatus status,
                             ProviderToken token, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static ProviderSession open(ProviderSessionId id, ProviderType providerType, ProviderToken token,
                                        Instant occurredAt) {
        return new ProviderSession(id, providerType, SessionStatus.ACTIVE, token, occurredAt, occurredAt);
    }

    public static ProviderSession reconstitute(ProviderSessionId id, ProviderType providerType, SessionStatus status,
                                                ProviderToken token, Instant createdAt, Instant updatedAt) {
        return new ProviderSession(id, providerType, status, token, createdAt, updatedAt);
    }

    /** Applies a successful {@code RefreshSession} call's new token. Only valid while
     * {@link SessionStatus#ACTIVE} — refreshing an already-terminal session is a programming
     * error in the caller (it should have re-authenticated instead), not a state this method
     * silently accepts. */
    public void applyRefreshedToken(ProviderToken newToken, Instant occurredAt) {
        if (status != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Cannot refresh a session that is not ACTIVE: " + status);
        }
        this.token = Objects.requireNonNull(newToken, "newToken must not be null");
        this.updatedAt = occurredAt;
    }

    /** @return {@code true} if this call changed state, {@code false} if already terminal. */
    public boolean expire(Instant occurredAt) {
        if (status != SessionStatus.ACTIVE) {
            return false;
        }
        this.status = SessionStatus.EXPIRED;
        this.updatedAt = occurredAt;
        return true;
    }

    /** @return {@code true} if this call changed state, {@code false} if already terminal. */
    public boolean revoke(Instant occurredAt) {
        if (status != SessionStatus.ACTIVE) {
            return false;
        }
        this.status = SessionStatus.REVOKED;
        this.updatedAt = occurredAt;
        return true;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public boolean isTokenExpired(Instant now) {
        return token.isExpired(now);
    }

    public ProviderSessionId id() {
        return id;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public SessionStatus status() {
        return status;
    }

    public ProviderToken token() {
        return token;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderSession other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
