package com.roadscanner.inventoryservice.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The canonical, merged catalog trip — docs/services/inventory-service/domain-model.md's
 * central aggregate. Carries no live seat state of any kind (that's
 * {@code provider-integration-service}'s concern entirely); everything here is catalog shape.
 *
 * Idempotency follows {@code search-service}'s {@code SearchableTrip} pattern exactly (staleness
 * rejection via {@code lastEventAt}, terminal cancellation) for first-party ingestion, since both
 * services consume the same at-least-once, per-trip-partitioned event stream
 * (docs/architecture/event-catalog.md).
 */
public final class Trip {

    private final TripId id;
    private RouteId routeId;
    private String origin;
    private String destination;
    private TripSchedule schedule;
    private UUID operatorId;
    private String operatorDisplayName;
    private UUID busId;
    private String busTypeCategory;
    private List<String> amenities;
    private FareAmount fare;
    private boolean bookable;
    private final SupplyOrigin supplyOrigin;
    private final Instant createdAt;
    private Instant lastEventAt;

    private Trip(TripId id, RouteId routeId, String origin, String destination, TripSchedule schedule,
                 UUID operatorId, String operatorDisplayName, UUID busId, String busTypeCategory,
                 List<String> amenities, FareAmount fare, boolean bookable, SupplyOrigin supplyOrigin,
                 Instant createdAt, Instant lastEventAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.routeId = routeId;
        this.origin = requireNonBlank(origin, "origin");
        this.destination = requireNonBlank(destination, "destination");
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
        this.operatorId = operatorId;
        this.operatorDisplayName = requireNonBlank(operatorDisplayName, "operatorDisplayName");
        this.busId = busId;
        this.busTypeCategory = requireNonBlank(busTypeCategory, "busTypeCategory");
        this.amenities = List.copyOf(Objects.requireNonNull(amenities, "amenities must not be null"));
        this.fare = Objects.requireNonNull(fare, "fare must not be null");
        this.bookable = bookable;
        this.supplyOrigin = Objects.requireNonNull(supplyOrigin, "supplyOrigin must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastEventAt = Objects.requireNonNull(lastEventAt, "lastEventAt must not be null");
    }

    /** Ingests a first-party {@code TripPublished} event — always starts bookable, id inherited
     * from the event, never minted here. */
    public static Trip ingestFirstParty(TripId id, RouteId routeId, String origin, String destination,
                                         TripSchedule schedule, UUID operatorId, String operatorDisplayName,
                                         UUID busId, String busTypeCategory, List<String> amenities,
                                         FareAmount fare, Instant occurredAt) {
        return new Trip(id, routeId, origin, destination, schedule, operatorId, operatorDisplayName, busId,
                busTypeCategory, amenities, fare, true, SupplyOrigin.FIRST_PARTY, occurredAt, occurredAt);
    }

    /** Creates a provider-synced trip discovered by catalog synchronization — this service mints
     * the id (there is no upstream trip id to inherit); no {@code operatorId} (provider-sourced
     * trips have no first-party operator account), only a display name copied from the
     * provider's own response. */
    public static Trip createFromProviderSync(TripId id, RouteId routeId, String origin, String destination,
                                                TripSchedule schedule, String operatorDisplayName,
                                                String busTypeCategory, FareAmount fare, Instant now) {
        return new Trip(id, routeId, origin, destination, schedule, null, operatorDisplayName, null,
                busTypeCategory, List.of(), fare, true, SupplyOrigin.PROVIDER_SYNCED, now, now);
    }

    public static Trip reconstitute(TripId id, RouteId routeId, String origin, String destination,
                                     TripSchedule schedule, UUID operatorId, String operatorDisplayName,
                                     UUID busId, String busTypeCategory, List<String> amenities, FareAmount fare,
                                     boolean bookable, SupplyOrigin supplyOrigin, Instant createdAt, Instant lastEventAt) {
        return new Trip(id, routeId, origin, destination, schedule, operatorId, operatorDisplayName, busId,
                busTypeCategory, amenities, fare, bookable, supplyOrigin, createdAt, lastEventAt);
    }

    /** Applies a {@code TripUpdated} event (first-party) or a re-sync reconciliation
     * (provider-synced) as a full-snapshot overwrite — matching {@code SearchableTrip.applyUpdate}'s
     * idempotency reasoning exactly: applying the same snapshot twice yields the same state.
     *
     * @return {@code true} if applied, {@code false} if rejected as stale or terminal (cancelled) —
     * callers use this only for logging, never to raise an error. */
    public boolean applyUpdate(RouteId routeId, String origin, String destination, TripSchedule schedule,
                                String operatorDisplayName, String busTypeCategory, List<String> amenities,
                                FareAmount fare, Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!bookable) {
            return false;
        }
        if (!occurredAt.isAfter(lastEventAt)) {
            return false;
        }
        this.routeId = routeId;
        this.origin = requireNonBlank(origin, "origin");
        this.destination = requireNonBlank(destination, "destination");
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
        this.operatorDisplayName = requireNonBlank(operatorDisplayName, "operatorDisplayName");
        this.busTypeCategory = requireNonBlank(busTypeCategory, "busTypeCategory");
        this.amenities = List.copyOf(amenities);
        this.fare = Objects.requireNonNull(fare, "fare must not be null");
        this.lastEventAt = occurredAt;
        return true;
    }

    /** Applies a {@code TripCancelled} event — terminal, unconditional, idempotent, matching
     * {@code SearchableTrip.cancel} exactly: no "un-cancel" event exists on this platform. */
    public boolean cancel(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!bookable) {
            return false;
        }
        this.bookable = false;
        if (occurredAt.isAfter(lastEventAt)) {
            this.lastEventAt = occurredAt;
        }
        return true;
    }

    /** Refreshes the denormalized operator display name after an {@code OperatorUpdated} event —
     * independent of {@link #applyUpdate}'s freshness marker, since operator renames arrive on a
     * separate stream. */
    public void refreshOperatorDisplayName(String operatorDisplayName) {
        this.operatorDisplayName = requireNonBlank(operatorDisplayName, "operatorDisplayName");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public TripId id() {
        return id;
    }

    public Optional<RouteId> routeId() {
        return Optional.ofNullable(routeId);
    }

    public String origin() {
        return origin;
    }

    public String destination() {
        return destination;
    }

    public TripSchedule schedule() {
        return schedule;
    }

    public Optional<UUID> operatorId() {
        return Optional.ofNullable(operatorId);
    }

    public String operatorDisplayName() {
        return operatorDisplayName;
    }

    public Optional<UUID> busId() {
        return Optional.ofNullable(busId);
    }

    public String busTypeCategory() {
        return busTypeCategory;
    }

    public List<String> amenities() {
        return amenities;
    }

    public FareAmount fare() {
        return fare;
    }

    public boolean bookable() {
        return bookable;
    }

    public SupplyOrigin supplyOrigin() {
        return supplyOrigin;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastEventAt() {
        return lastEventAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trip other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
