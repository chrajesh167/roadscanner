package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence shape for {@code Trip} — {@code TripSchedule} and {@code FareAmount} are flattened
 * into plain columns rather than {@code @Embeddable}, matching {@code search-service}'s
 * {@code SearchableTripJpaEntity} precedent for the same value objects; {@code amenities} is a
 * single comma-joined column for the same reason that entity gives (display-only, no per-amenity
 * query need). No column anywhere on this table represents seat status of any kind.
 */
@Entity
@Table(name = "trips")
public class TripJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(name = "operator_display_name", nullable = false)
    private String operatorDisplayName;

    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "bus_type_category", nullable = false)
    private String busTypeCategory;

    @Column(name = "amenities")
    private String amenities;

    @Column(name = "fare_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "fare_currency", nullable = false)
    private String fareCurrency;

    @Column(name = "fare_captured_at", nullable = false)
    private Instant fareCapturedAt;

    @Column(name = "bookable", nullable = false)
    private boolean bookable;

    @Column(name = "supply_origin", nullable = false, updatable = false)
    private String supplyOrigin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TripJpaEntity() {
    }

    TripJpaEntity(UUID id, UUID routeId, String origin, String destination, Instant departureTime,
                  Instant arrivalTime, UUID operatorId, String operatorDisplayName, UUID busId,
                  String busTypeCategory, String amenities, BigDecimal fareAmount, String fareCurrency,
                  Instant fareCapturedAt, boolean bookable, String supplyOrigin, Instant createdAt, Instant lastEventAt) {
        this.id = id;
        this.routeId = routeId;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.operatorId = operatorId;
        this.operatorDisplayName = operatorDisplayName;
        this.busId = busId;
        this.busTypeCategory = busTypeCategory;
        this.amenities = amenities;
        this.fareAmount = fareAmount;
        this.fareCurrency = fareCurrency;
        this.fareCapturedAt = fareCapturedAt;
        this.bookable = bookable;
        this.supplyOrigin = supplyOrigin;
        this.createdAt = createdAt;
        this.lastEventAt = lastEventAt;
    }

    void applyMutableState(UUID routeId, String origin, String destination, Instant departureTime,
                            Instant arrivalTime, String operatorDisplayName, String busTypeCategory,
                            String amenities, BigDecimal fareAmount, String fareCurrency, Instant fareCapturedAt,
                            boolean bookable, Instant lastEventAt) {
        this.routeId = routeId;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.operatorDisplayName = operatorDisplayName;
        this.busTypeCategory = busTypeCategory;
        this.amenities = amenities;
        this.fareAmount = fareAmount;
        this.fareCurrency = fareCurrency;
        this.fareCapturedAt = fareCapturedAt;
        this.bookable = bookable;
        this.lastEventAt = lastEventAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRouteId() {
        return routeId;
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

    public UUID getOperatorId() {
        return operatorId;
    }

    public String getOperatorDisplayName() {
        return operatorDisplayName;
    }

    public UUID getBusId() {
        return busId;
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

    public Instant getFareCapturedAt() {
        return fareCapturedAt;
    }

    public boolean isBookable() {
        return bookable;
    }

    public String getSupplyOrigin() {
        return supplyOrigin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public long getVersion() {
        return version;
    }
}
