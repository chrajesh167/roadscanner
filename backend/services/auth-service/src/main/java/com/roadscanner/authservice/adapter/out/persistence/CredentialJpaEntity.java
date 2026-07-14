package com.roadscanner.authservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * The persistence-shape counterpart of {@link com.roadscanner.authservice.domain.model.Credential}.
 * Deliberately has zero compile-time dependency on {@code domain.model} — only
 * {@link CredentialMapper} bridges the two sides, per
 * docs/services/auth-service/package-structure.md's hexagonal boundary. {@code status} is
 * stored as a plain {@code String}, not the domain's {@code AccountStatus} enum, for the same
 * reason: this class must remain meaningful and compilable with no knowledge of the domain
 * layer at all.
 *
 * {@code loginIdentifier} and {@code createdAt} have no setter — the domain never changes them
 * after registration, so there is nothing for this entity to mutate either.
 */
@Entity
@Table(name = "credentials")
public class CredentialJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "login_identifier", nullable = false, updatable = false)
    private String loginIdentifier;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_algorithm_id", nullable = false)
    private String passwordAlgorithmId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA/Hibernate for entity instantiation via reflection. */
    protected CredentialJpaEntity() {
    }

    CredentialJpaEntity(UUID id, String loginIdentifier, String passwordHash, String passwordAlgorithmId,
                         String status, int failedLoginAttempts, Instant createdAt, Instant updatedAt,
                         Instant lastLoginAt) {
        this.id = id;
        this.loginIdentifier = loginIdentifier;
        this.passwordHash = passwordHash;
        this.passwordAlgorithmId = passwordAlgorithmId;
        this.status = status;
        this.failedLoginAttempts = failedLoginAttempts;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
    }

    /** Applies the mutable fields of an updated aggregate onto this managed entity, preserving
     * {@code version} for Hibernate's own optimistic-lock bookkeeping — see
     * {@link CredentialRepositoryAdapter} for why this matters. */
    void applyMutableState(String passwordHash, String passwordAlgorithmId, String status,
                            int failedLoginAttempts, Instant updatedAt, Instant lastLoginAt) {
        this.passwordHash = passwordHash;
        this.passwordAlgorithmId = passwordAlgorithmId;
        this.status = status;
        this.failedLoginAttempts = failedLoginAttempts;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
    }

    public UUID getId() {
        return id;
    }

    public String getLoginIdentifier() {
        return loginIdentifier;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPasswordAlgorithmId() {
        return passwordAlgorithmId;
    }

    public String getStatus() {
        return status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public long getVersion() {
        return version;
    }
}
