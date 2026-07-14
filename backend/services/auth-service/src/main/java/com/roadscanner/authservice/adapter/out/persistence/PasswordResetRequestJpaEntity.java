package com.roadscanner.authservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * The persistence-shape counterpart of
 * {@link com.roadscanner.authservice.domain.model.PasswordResetRequest}. Only {@code usedAt} is
 * mutable after creation — {@code use()} is the only state transition the domain aggregate
 * supports post-issuance.
 */
@Entity
@Table(name = "password_reset_requests")
public class PasswordResetRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PasswordResetRequestJpaEntity() {
    }

    PasswordResetRequestJpaEntity(UUID id, String tokenHash, UUID userId, Instant expiresAt, Instant usedAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    /** Preserves {@code version} for Hibernate's optimistic-lock bookkeeping — see
     * {@link PasswordResetRequestRepositoryAdapter}. */
    void applyMutableState(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public long getVersion() {
        return version;
    }
}
