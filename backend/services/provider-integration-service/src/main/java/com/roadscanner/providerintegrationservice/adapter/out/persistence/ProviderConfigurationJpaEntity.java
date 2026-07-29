package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Persistence shape for {@code Provider} — zero compile-time dependency on {@code domain.model},
 * matching {@code search-service}'s {@code SearchableTripJpaEntity} discipline. Read-only from
 * this service's own code (see {@code ProviderConfigurationRepository}'s Javadoc): rows are
 * managed exclusively via Flyway seed migrations, never written by application code. */
@Entity
@Table(name = "provider_configurations")
public class ProviderConfigurationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_type", nullable = false, updatable = false)
    private String providerType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "capabilities", nullable = false)
    private String capabilities;

    @Column(name = "provider_category", nullable = false)
    private String providerCategory;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProviderConfigurationJpaEntity() {
    }

    ProviderConfigurationJpaEntity(UUID id, String providerType, String providerCategory, String displayName,
                                   boolean enabled, String capabilities, String baseUrl, int timeoutMs,
                                   int retryCount, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.providerType = providerType;
        this.providerCategory = providerCategory;
        this.displayName = displayName;
        this.enabled = enabled;
        this.capabilities = capabilities;
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public String getBaseUrl() {
        return baseUrl;
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

    public String getProviderCategory() {
        return providerCategory;
    }

    void setProviderCategory(String providerCategory) {
        this.providerCategory = providerCategory;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
