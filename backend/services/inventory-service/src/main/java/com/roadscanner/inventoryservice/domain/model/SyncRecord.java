package com.roadscanner.inventoryservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One catalog-synchronization attempt against one provider — synchronization metadata this
 * service owns outright (docs/services/inventory-service/domain-model.md's {@code SyncRecord}).
 * {@code catalogVersion} is a simple, monotonically-increasing per-provider counter — a coarse
 * "how many times has this been reconciled" marker, not a full versioning scheme. */
public final class SyncRecord {

    private final SyncRecordId id;
    private final ProviderType providerType;
    private Instant lastAttemptAt;
    private SyncStatus status;
    private long catalogVersion;
    private String errorDetail;

    private SyncRecord(SyncRecordId id, ProviderType providerType, Instant lastAttemptAt, SyncStatus status,
                        long catalogVersion, String errorDetail) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.lastAttemptAt = Objects.requireNonNull(lastAttemptAt, "lastAttemptAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.catalogVersion = catalogVersion;
        this.errorDetail = errorDetail;
    }

    public static SyncRecord start(SyncRecordId id, ProviderType providerType, Instant now) {
        return new SyncRecord(id, providerType, now, SyncStatus.IN_PROGRESS, 0L, null);
    }

    public static SyncRecord reconstitute(SyncRecordId id, ProviderType providerType, Instant lastAttemptAt,
                                           SyncStatus status, long catalogVersion, String errorDetail) {
        return new SyncRecord(id, providerType, lastAttemptAt, status, catalogVersion, errorDetail);
    }

    public void complete(Instant completedAt) {
        this.lastAttemptAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.status = SyncStatus.SUCCESS;
        this.catalogVersion = this.catalogVersion + 1;
        this.errorDetail = null;
    }

    public void fail(Instant failedAt, String errorDetail) {
        this.lastAttemptAt = Objects.requireNonNull(failedAt, "failedAt must not be null");
        this.status = SyncStatus.FAILED;
        this.errorDetail = errorDetail;
    }

    public SyncRecordId id() {
        return id;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public SyncStatus status() {
        return status;
    }

    public long catalogVersion() {
        return catalogVersion;
    }

    public Optional<String> errorDetail() {
        return Optional.ofNullable(errorDetail);
    }
}
