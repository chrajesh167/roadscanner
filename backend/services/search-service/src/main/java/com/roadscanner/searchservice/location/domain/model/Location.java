package com.roadscanner.searchservice.location.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * RoadScanner's master record for a place — the canonical location catalogue entry the whole
 * platform resolves against.
 *
 * <p>Unlike {@code SearchableTrip}, which is a disposable projection rebuilt from Kafka, this is
 * a genuine DDD aggregate root holding authored state and protecting real invariants:
 *
 * <ul>
 *   <li><strong>Identity is ours.</strong> {@link LocationId} is minted here and never derived
 *       from a provider or from Google, so the platform's identifiers survive a provider being
 *       replaced.</li>
 *   <li><strong>Deactivation is not deletion.</strong> A location can be referenced by historical
 *       trips and by provider mappings, so it must stay resolvable forever. {@link #disable()}
 *       is the only removal path and it is idempotent.</li>
 *   <li><strong>Shape is always valid.</strong> Required fields are enforced in the constructor,
 *       so no caller — REST, a future importer, a test — can produce an invalid Location.</li>
 * </ul>
 *
 * <p>The aggregate deliberately knows nothing about providers. A {@link ProviderLocationMapping}
 * points <em>at</em> a Location by id; the Location holds no collection of mappings. That keeps
 * the aggregate small, lets Sprint 3 add mappings for a new provider without touching this class,
 * and avoids loading N provider rows to answer an autocomplete query.
 */
public final class Location {

    private final LocationId id;
    private String displayName;
    private LocationAddress address;
    private GeoCoordinates coordinates;
    private GooglePlaceId googlePlaceId;
    private String timezone;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private Location(LocationId id, String displayName, LocationAddress address, GeoCoordinates coordinates,
                     GooglePlaceId googlePlaceId, String timezone, boolean active,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.displayName = requireValidDisplayName(displayName);
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.coordinates = coordinates;
        this.googlePlaceId = googlePlaceId;
        this.timezone = normaliseTimezone(timezone);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** Creates a brand-new catalogue entry. Always starts active. */
    public static Location create(LocationId id, String displayName, LocationAddress address,
                                  GeoCoordinates coordinates, GooglePlaceId googlePlaceId, String timezone,
                                  Instant now) {
        return new Location(id, displayName, address, coordinates, googlePlaceId, timezone, true, now, now);
    }

    /** Rehydrates from persisted state. Trusts the state is already valid. */
    public static Location reconstitute(LocationId id, String displayName, LocationAddress address,
                                        GeoCoordinates coordinates, GooglePlaceId googlePlaceId, String timezone,
                                        boolean active, Instant createdAt, Instant updatedAt) {
        return new Location(id, displayName, address, coordinates, googlePlaceId, timezone, active,
                createdAt, updatedAt);
    }

    /**
     * Applies an edit as a full-snapshot overwrite of the mutable fields, mirroring the
     * replace-not-patch semantics {@code SearchableTrip.applyUpdate} uses — a PUT that omits a
     * field means "clear it", not "leave whatever was there", so the stored record always matches
     * exactly what the caller last asserted.
     *
     * <p>{@code active} is intentionally not settable here: reactivation and deactivation are
     * distinct operations with their own audit meaning, not incidental side effects of an edit.
     */
    public void update(String displayName, LocationAddress address, GeoCoordinates coordinates,
                       GooglePlaceId googlePlaceId, String timezone, Instant now) {
        this.displayName = requireValidDisplayName(displayName);
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.coordinates = coordinates;
        this.googlePlaceId = googlePlaceId;
        this.timezone = normaliseTimezone(timezone);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * Soft-deletes. Idempotent: disabling an already-disabled location is a no-op rather than an
     * error, so a retried DELETE behaves identically to the first one.
     *
     * @return true if this call changed the state
     */
    public boolean disable(Instant now) {
        if (!active) {
            return false;
        }
        this.active = false;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    /** Restores a soft-deleted location. Idempotent, for the same reason {@link #disable} is. */
    public boolean activate(Instant now) {
        if (active) {
            return false;
        }
        this.active = true;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    /**
     * Attaches a Google place id discovered by enrichment.
     *
     * <p>Nothing calls this in Sprint 1 — it exists so Sprint 2's enrichment has a domain
     * operation to invoke instead of reaching in and mutating a field, and so the "never silently
     * repoint an existing association" rule lives in the aggregate rather than in the enricher.
     */
    public boolean attachGooglePlaceId(GooglePlaceId candidate, Instant now) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (this.googlePlaceId != null) {
            return this.googlePlaceId.equals(candidate);
        }
        this.googlePlaceId = candidate;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    public LocationId id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public LocationAddress address() {
        return address;
    }

    public Optional<GeoCoordinates> coordinates() {
        return Optional.ofNullable(coordinates);
    }

    public Optional<GooglePlaceId> googlePlaceId() {
        return Optional.ofNullable(googlePlaceId);
    }

    public Optional<String> timezone() {
        return Optional.ofNullable(timezone);
    }

    public boolean isActive() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String requireValidDisplayName(String value) {
        Objects.requireNonNull(value, "displayName must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("displayName must be at most 255 characters");
        }
        return trimmed;
    }

    private static String normaliseTimezone(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // Aggregate identity is the id alone — two loads of the same row are the same Location
        // regardless of field drift, matching how auth-service compares its aggregates.
        return other instanceof Location location && id.equals(location.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Location[" + id + " " + displayName + "]";
    }
}
