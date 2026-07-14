package com.roadscanner.authservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * The persistence-shape counterpart of {@link com.roadscanner.authservice.domain.model.RefreshToken}.
 * {@code userId} and {@code replacesTokenId} are plain UUID columns, not JPA {@code @ManyToOne}
 * relationships — the domain never needs to navigate an object graph from a refresh token back
 * to its Credential or predecessor, only the raw id value, so modeling a relationship here would
 * add lazy-loading/proxy complexity for zero query benefit (see
 * docs/services/auth-service/database-design.md's "keep entity mappings optimized").
 *
 * Only {@code revokedAt} is mutable after creation — {@code revoke()}/{@code rotate()} are the
 * only state transitions {@code RefreshToken} supports post-issuance.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaces_token_id", updatable = false)
    private UUID replacesTokenId;

    @Column(name = "device_label", updatable = false)
    private String deviceLabel;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RefreshTokenJpaEntity() {
    }

    RefreshTokenJpaEntity(UUID id, String tokenHash, UUID userId, Instant issuedAt, Instant expiresAt,
                           Instant revokedAt, UUID replacesTokenId, String deviceLabel) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacesTokenId = replacesTokenId;
        this.deviceLabel = deviceLabel;
    }

    /** Preserves {@code version} for Hibernate's optimistic-lock bookkeeping — see
     * {@link RefreshTokenRepositoryAdapter}. */
    void applyMutableState(Instant revokedAt) {
        this.revokedAt = revokedAt;
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

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacesTokenId() {
        return replacesTokenId;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public long getVersion() {
        return version;
    }
}
