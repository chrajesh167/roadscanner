package com.roadscanner.searchservice.location.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence shape for {@link com.roadscanner.searchservice.location.domain.model.Location}.
 * Zero compile-time dependency on {@code domain.model} — only {@link LocationMapper} bridges the
 * two, matching {@code SearchableTripJpaEntity}'s discipline.
 *
 * <p>{@code id} and {@code createdAt} have no setters: both are fixed for the row's lifetime.
 */
@Entity
@Table(name = "location")
public class LocationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "state", length = 120)
    private String state;

    @Column(name = "country", nullable = false, length = 120)
    private String country;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "timezone", length = 80)
    private String timezone;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LocationJpaEntity() {
        // Required by JPA.
    }

    LocationJpaEntity(UUID id, String displayName, String city, String state, String country, BigDecimal latitude,
                      BigDecimal longitude, String googlePlaceId, String timezone, boolean active,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.city = city;
        this.state = state;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.googlePlaceId = googlePlaceId;
        this.timezone = timezone;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getDisplayName() {
        return displayName;
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    String getCity() {
        return city;
    }

    void setCity(String city) {
        this.city = city;
    }

    String getState() {
        return state;
    }

    void setState(String state) {
        this.state = state;
    }

    String getCountry() {
        return country;
    }

    void setCountry(String country) {
        this.country = country;
    }

    BigDecimal getLatitude() {
        return latitude;
    }

    void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    BigDecimal getLongitude() {
        return longitude;
    }

    void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    String getGooglePlaceId() {
        return googlePlaceId;
    }

    void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    String getTimezone() {
        return timezone;
    }

    void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
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
