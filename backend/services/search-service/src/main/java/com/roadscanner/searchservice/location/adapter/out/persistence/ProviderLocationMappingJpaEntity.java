package com.roadscanner.searchservice.location.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence shape for
 * {@link com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping}.
 *
 * <p>{@code locationId} is stored as a plain UUID column rather than a JPA {@code @ManyToOne} to
 * {@link LocationJpaEntity}. The foreign key still exists in the database (V2's
 * {@code fk_provider_location}), but modelling it as an association here would let a caller
 * traverse from a mapping into a Location entity and back, quietly re-coupling two aggregates
 * that the domain deliberately keeps independent — and would drag a join into every mapping read.
 * This is the same reason {@code SearchableTripJpaEntity} holds {@code operatorId} as a bare UUID.
 *
 * <p>{@code providerMetadata} is mapped to Postgres {@code JSONB} via {@code @JdbcTypeCode} and
 * carried as an opaque {@code String} — the payload's shape belongs to the provider, so parsing
 * it here would couple this module to whichever provider was onboarded first.
 */
@Entity
@Table(name = "provider_location_mapping")
public class ProviderLocationMappingJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 50, updatable = false)
    private String provider;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "provider_city_id", length = 255)
    private String providerCityId;

    @Column(name = "provider_station_id", length = 255)
    private String providerStationId;

    @Column(name = "provider_station_name", length = 255)
    private String providerStationName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_metadata")
    private String providerMetadata;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "last_synced")
    private Instant lastSynced;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderLocationMappingJpaEntity() {
        // Required by JPA.
    }

    ProviderLocationMappingJpaEntity(UUID id, String provider, UUID locationId, String providerCityId,
                                     String providerStationId, String providerStationName, String providerMetadata,
                                     boolean verified, Instant lastSynced, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.provider = provider;
        this.locationId = locationId;
        this.providerCityId = providerCityId;
        this.providerStationId = providerStationId;
        this.providerStationName = providerStationName;
        this.providerMetadata = providerMetadata;
        this.verified = verified;
        this.lastSynced = lastSynced;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getProvider() {
        return provider;
    }

    UUID getLocationId() {
        return locationId;
    }

    String getProviderCityId() {
        return providerCityId;
    }

    void setProviderCityId(String providerCityId) {
        this.providerCityId = providerCityId;
    }

    String getProviderStationId() {
        return providerStationId;
    }

    void setProviderStationId(String providerStationId) {
        this.providerStationId = providerStationId;
    }

    String getProviderStationName() {
        return providerStationName;
    }

    void setProviderStationName(String providerStationName) {
        this.providerStationName = providerStationName;
    }

    String getProviderMetadata() {
        return providerMetadata;
    }

    void setProviderMetadata(String providerMetadata) {
        this.providerMetadata = providerMetadata;
    }

    boolean isVerified() {
        return verified;
    }

    void setVerified(boolean verified) {
        this.verified = verified;
    }

    Instant getLastSynced() {
        return lastSynced;
    }

    void setLastSynced(Instant lastSynced) {
        this.lastSynced = lastSynced;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
