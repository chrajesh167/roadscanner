package com.roadscanner.searchservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The persistence-shape counterpart of
 * {@link com.roadscanner.searchservice.domain.model.SearchableTrip}. Deliberately has zero
 * compile-time dependency on {@code domain.model} — only {@link SearchableTripMapper} bridges
 * the two sides, matching {@code auth-service}'s {@code CredentialJpaEntity} discipline.
 *
 * {@code amenities} is stored as a single comma-joined column rather than a Postgres array or
 * a JSON column — this is a display-only list with no query requirement of its own (nothing
 * filters or sorts by a specific amenity), so a delimited string avoids the extra Hibernate
 * array-type mapping complexity for no query benefit.
 *
 * {@code tripId} has no setter — it is the natural key from the upstream event, immutable for
 * the row's entire lifetime, and {@code operatorId}/{@code createdAt} are likewise never
 * changed after creation.
 */
@Entity
@Table(name = "searchable_trips")
public class SearchableTripJpaEntity {

    @Id
    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "operator_id", nullable = false, updatable = false)
    private UUID operatorId;

    @Column(name = "operator_name", nullable = false)
    private String operatorName;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    /** Database-generated (see V1 migration) — never written by this entity, read only so a
     * {@code Sort} can reference it by property name for "sort by duration" queries. */
    @Column(name = "duration_seconds", insertable = false, updatable = false)
    private Integer durationSeconds;

    @Column(name = "bus_type_category", nullable = false)
    private String busTypeCategory;

    @Column(name = "amenities")
    private String amenities;

    @Column(name = "fare_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "fare_currency", nullable = false)
    private String fareCurrency;

    @Column(name = "bookable", nullable = false)
    private boolean bookable;

    @Column(name = "rating_average", nullable = false)
    private double ratingAverage;

    @Column(name = "rating_review_count", nullable = false)
    private int ratingReviewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_trip_event_at", nullable = false)
    private Instant lastTripEventAt;

    @Column(name = "last_rating_event_at", nullable = false)
    private Instant lastRatingEventAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA/Hibernate for entity instantiation via reflection. */
    protected SearchableTripJpaEntity() {
    }

    SearchableTripJpaEntity(UUID tripId, UUID operatorId, String operatorName, String origin, String destination,
                             Instant departureTime, Instant arrivalTime, String busTypeCategory, String amenities,
                             BigDecimal fareAmount, String fareCurrency, boolean bookable, double ratingAverage,
                             int ratingReviewCount, Instant createdAt, Instant lastTripEventAt,
                             Instant lastRatingEventAt) {
        this.tripId = tripId;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.busTypeCategory = busTypeCategory;
        this.amenities = amenities;
        this.fareAmount = fareAmount;
        this.fareCurrency = fareCurrency;
        this.bookable = bookable;
        this.ratingAverage = ratingAverage;
        this.ratingReviewCount = ratingReviewCount;
        this.createdAt = createdAt;
        this.lastTripEventAt = lastTripEventAt;
        this.lastRatingEventAt = lastRatingEventAt;
    }

    /** Applies the mutable fields of an updated aggregate onto this managed entity, preserving
     * {@code version} for Hibernate's own optimistic-lock bookkeeping — see
     * {@link SearchableTripRepositoryAdapter} for why this matters. */
    void applyMutableState(String operatorName, String origin, String destination, Instant departureTime,
                            Instant arrivalTime, String busTypeCategory, String amenities, BigDecimal fareAmount,
                            String fareCurrency, boolean bookable, double ratingAverage, int ratingReviewCount,
                            Instant lastTripEventAt, Instant lastRatingEventAt) {
        this.operatorName = operatorName;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.busTypeCategory = busTypeCategory;
        this.amenities = amenities;
        this.fareAmount = fareAmount;
        this.fareCurrency = fareCurrency;
        this.bookable = bookable;
        this.ratingAverage = ratingAverage;
        this.ratingReviewCount = ratingReviewCount;
        this.lastTripEventAt = lastTripEventAt;
        this.lastRatingEventAt = lastRatingEventAt;
    }

    public UUID getTripId() {
        return tripId;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public Instant getArrivalTime() {
        return arrivalTime;
    }

    public String getBusTypeCategory() {
        return busTypeCategory;
    }

    public String getAmenities() {
        return amenities;
    }

    public BigDecimal getFareAmount() {
        return fareAmount;
    }

    public String getFareCurrency() {
        return fareCurrency;
    }

    public boolean isBookable() {
        return bookable;
    }

    public double getRatingAverage() {
        return ratingAverage;
    }

    public int getRatingReviewCount() {
        return ratingReviewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastTripEventAt() {
        return lastTripEventAt;
    }

    public Instant getLastRatingEventAt() {
        return lastRatingEventAt;
    }

    public long getVersion() {
        return version;
    }
}
