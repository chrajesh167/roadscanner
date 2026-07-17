package com.roadscanner.searchservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The read-model projection at the center of this service — see
 * docs/services/search-service/domain-model.md for why this is a projection with no protected
 * business invariant of its own, not a DDD aggregate root in the sense
 * {@code auth-service}'s {@code Credential} is. It still encodes two real rules, though, the
 * same way {@code RefreshToken.rotate} encodes reuse-detection as the only way to rotate at
 * all: staleness rejection and terminal-state rejection, both driven directly by
 * docs/services/search-service/events-consumed.md's at-least-once, no-cross-topic-ordering
 * delivery model.
 *
 * <p>Identified by {@link TripId} — the same id {@code inventory-service}/{@code
 * operator-service} use, so a result can be acted on without translation
 * (docs/services/search-service/domain-model.md).
 */
public final class SearchableTrip {

    private final TripId tripId;
    private final OperatorId operatorId;
    private String operatorName;
    private Route route;
    private Schedule schedule;
    private BusType busType;
    private FareSnapshot fare;
    private boolean bookable;
    private RatingSnapshot rating;
    private final Instant createdAt;
    private Instant lastTripEventAt;
    private Instant lastRatingEventAt;

    private SearchableTrip(TripId tripId, OperatorId operatorId, String operatorName, Route route,
                           Schedule schedule, BusType busType, FareSnapshot fare, boolean bookable,
                           RatingSnapshot rating, Instant createdAt, Instant lastTripEventAt,
                           Instant lastRatingEventAt) {
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.operatorId = Objects.requireNonNull(operatorId, "operatorId must not be null");
        this.operatorName = requireNonBlank(operatorName, "operatorName");
        this.route = Objects.requireNonNull(route, "route must not be null");
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
        this.busType = Objects.requireNonNull(busType, "busType must not be null");
        this.fare = Objects.requireNonNull(fare, "fare must not be null");
        this.bookable = bookable;
        this.rating = Objects.requireNonNull(rating, "rating must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastTripEventAt = Objects.requireNonNull(lastTripEventAt, "lastTripEventAt must not be null");
        this.lastRatingEventAt = Objects.requireNonNull(lastRatingEventAt, "lastRatingEventAt must not be null");
    }

    /**
     * Indexes a newly published trip — the head of this projection's lifecycle
     * (docs/services/search-service/use-cases.md, "Index a Newly Published Trip"). Always
     * starts bookable, with no rating yet.
     */
    public static SearchableTrip publish(TripId tripId, OperatorId operatorId, String operatorName, Route route,
                                          Schedule schedule, BusType busType, FareSnapshot fare, Instant occurredAt) {
        return new SearchableTrip(tripId, operatorId, operatorName, route, schedule, busType, fare,
                true, RatingSnapshot.none(), occurredAt, occurredAt, occurredAt);
    }

    /** Rehydrates a SearchableTrip from persisted state. Trusts the state is already valid. */
    public static SearchableTrip reconstitute(TripId tripId, OperatorId operatorId, String operatorName, Route route,
                                               Schedule schedule, BusType busType, FareSnapshot fare, boolean bookable,
                                               RatingSnapshot rating, Instant createdAt, Instant lastTripEventAt,
                                               Instant lastRatingEventAt) {
        return new SearchableTrip(tripId, operatorId, operatorName, route, schedule, busType, fare,
                bookable, rating, createdAt, lastTripEventAt, lastRatingEventAt);
    }

    /**
     * Applies a {@code TripUpdated} event as a full-snapshot overwrite (fare, schedule,
     * operator/bus-type shape) — a full replace rather than a partial patch, which is what
     * keeps this method's idempotency simple: applying the same snapshot twice yields the same
     * state, with no field-by-field merge logic that could disagree with itself on redelivery.
     *
     * <p>Rejects the update, as a no-op, in exactly two cases — both direct consequences of
     * docs/services/search-service/events-consumed.md's delivery model, not incidental
     * validation:
     * <ul>
     *   <li>the trip is already cancelled — a terminal state (see {@link #cancel}); an
     *       operator does not update a cancelled trip, so a late-arriving update for one is
     *       treated as stale rather than un-cancelling it</li>
     *   <li>{@code occurredAt} is not after the last applied trip event — an out-of-order
     *       redelivery of an already-superseded event, which
     *       docs/architecture/event-catalog.md's at-least-once model guarantees will happen
     *       occasionally</li>
     * </ul>
     *
     * @return {@code true} if the update was applied, {@code false} if it was rejected as
     *         stale or terminal — callers use this only for logging, never to raise an error;
     *         per docs/services/search-service/events-consumed.md, a rejected redelivery is
     *         expected behavior, not a failure.
     */
    public boolean applyUpdate(String operatorName, Route route, Schedule schedule, BusType busType,
                                FareSnapshot fare, Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!bookable) {
            return false;
        }
        if (!occurredAt.isAfter(lastTripEventAt)) {
            return false;
        }
        this.operatorName = requireNonBlank(operatorName, "operatorName");
        this.route = Objects.requireNonNull(route, "route must not be null");
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
        this.busType = Objects.requireNonNull(busType, "busType must not be null");
        this.fare = Objects.requireNonNull(fare, "fare must not be null");
        this.lastTripEventAt = occurredAt;
        return true;
    }

    /**
     * Applies a {@code TripCancelled} event. Unconditionally applied once — unlike
     * {@link #applyUpdate}, cancellation does not check {@code occurredAt} against
     * {@link #lastTripEventAt}: per docs/services/search-service/events-consumed.md, this
     * event is the platform's highest-stakes signal for this data, and there is no
     * "un-cancel" event in docs/architecture/event-catalog.md to ever legitimately reverse it
     * — the only question worth asking is whether it was already applied.
     *
     * @return {@code true} if this call changed state, {@code false} if the trip was already
     *         cancelled (idempotent no-op, matching {@code RefreshToken.revoke}'s pattern in
     *         {@code auth-service}).
     */
    public boolean cancel(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!bookable) {
            return false;
        }
        this.bookable = false;
        if (occurredAt.isAfter(lastTripEventAt)) {
            this.lastTripEventAt = occurredAt;
        }
        return true;
    }

    /**
     * Applies a {@code ReviewSubmitted} event's already-computed aggregate
     * (docs/services/search-service/domain-model.md — {@code review-service} owns the
     * average-calculation logic, this method only copies the result). Guarded by its own
     * freshness marker, independent of {@link #lastTripEventAt}, since rating events arrive on
     * a completely separate stream from trip lifecycle events
     * (docs/services/search-service/events-consumed.md).
     *
     * @return {@code true} if applied, {@code false} if rejected as a stale redelivery.
     */
    public boolean applyRatingUpdate(RatingSnapshot newRating, Instant occurredAt) {
        Objects.requireNonNull(newRating, "newRating must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!occurredAt.isAfter(lastRatingEventAt)) {
            return false;
        }
        this.rating = newRating;
        this.lastRatingEventAt = occurredAt;
        return true;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public TripId tripId() {
        return tripId;
    }

    public OperatorId operatorId() {
        return operatorId;
    }

    public String operatorName() {
        return operatorName;
    }

    public Route route() {
        return route;
    }

    public Schedule schedule() {
        return schedule;
    }

    public BusType busType() {
        return busType;
    }

    public FareSnapshot fare() {
        return fare;
    }

    public boolean bookable() {
        return bookable;
    }

    public RatingSnapshot rating() {
        return rating;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastTripEventAt() {
        return lastTripEventAt;
    }

    public Instant lastRatingEventAt() {
        return lastRatingEventAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchableTrip other)) return false;
        return tripId.equals(other.tripId);
    }

    @Override
    public int hashCode() {
        return tripId.hashCode();
    }
}
