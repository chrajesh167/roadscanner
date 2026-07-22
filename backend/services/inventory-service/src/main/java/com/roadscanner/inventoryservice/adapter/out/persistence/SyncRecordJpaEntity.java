package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One row per synchronization attempt — insert-mostly history, matching
 * docs/services/inventory-service/data-ownership.md's retention note ("SyncRecord history can
 * be pruned on a simple schedule"); pruning itself is an operational task, not implemented here. */
@Entity
@Table(name = "sync_records")
public class SyncRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_type", nullable = false, updatable = false)
    private String providerType;

    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "catalog_version", nullable = false)
    private long catalogVersion;

    @Column(name = "error_detail")
    private String errorDetail;

    protected SyncRecordJpaEntity() {
    }

    SyncRecordJpaEntity(UUID id, String providerType, Instant lastAttemptAt, String status, long catalogVersion, String errorDetail) {
        this.id = id;
        this.providerType = providerType;
        this.lastAttemptAt = lastAttemptAt;
        this.status = status;
        this.catalogVersion = catalogVersion;
        this.errorDetail = errorDetail;
    }

    void applyMutableState(Instant lastAttemptAt, String status, long catalogVersion, String errorDetail) {
        this.lastAttemptAt = lastAttemptAt;
        this.status = status;
        this.catalogVersion = catalogVersion;
        this.errorDetail = errorDetail;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderType() {
        return providerType;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getStatus() {
        return status;
    }

    public long getCatalogVersion() {
        return catalogVersion;
    }

    public String getErrorDetail() {
        return errorDetail;
    }
}
