package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Persistence shape for {@code ProviderSession} — see {@code V2__create_provider_sessions.sql}
 * for why tokens are stored as plain columns rather than hashed. */
@Entity
@Table(name = "provider_sessions")
public class ProviderSessionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_type", nullable = false, updatable = false)
    private String providerType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "token_type", nullable = false)
    private String tokenType;

    @Column(name = "token_expires_at", nullable = false)
    private Instant tokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProviderSessionJpaEntity() {
    }

    ProviderSessionJpaEntity(UUID id, String providerType, String status, String accessToken, String refreshToken,
                              String tokenType, Instant tokenExpiresAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.providerType = providerType;
        this.status = status;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.tokenExpiresAt = tokenExpiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Updates an already-managed entity in place, preserving {@code version} for Hibernate's
     * optimistic-lock bookkeeping — matching {@code SearchableTripJpaEntity.applyMutableState}. */
    void applyMutableState(String status, String accessToken, String refreshToken, String tokenType,
                            Instant tokenExpiresAt, Instant updatedAt) {
        this.status = status;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.tokenExpiresAt = tokenExpiresAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getStatus() {
        return status;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
