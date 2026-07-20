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
}
