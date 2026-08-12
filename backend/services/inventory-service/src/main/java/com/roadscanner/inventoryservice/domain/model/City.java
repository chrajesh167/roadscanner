package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Structured catalog geography — new to the platform, per docs/services/inventory-service/domain-model.md.
 * Owned outright by this service, kept current via administrative catalog management, not
 * event-driven (there is no upstream "City" concept anywhere else on the platform). */
public final class City {

    private final CityId id;
    private String name;
    private String state;
    private String country;
    private final UUID locationId;

    private City(CityId id, String name, String state, String country, UUID locationId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = requireNonBlank(name, "name");
        this.state = requireNonBlank(state, "state");
        this.country = requireNonBlank(country, "country");
        this.locationId = locationId;
    }

    public static City create(CityId id, String name, String state, String country) {
        return new City(id, name, state, country, null);
    }

    public static City reconstitute(CityId id, String name, String state, String country, UUID locationId) {
        return new City(id, name, state, country, locationId);
    }

    /**
     * The canonical RoadScanner location this city is, where an administrator has recorded one.
     *
     * <p>Absent means this city cannot be translated into any provider's vocabulary — the
     * translation table is keyed by canonical location — so catalog sync skips it rather than
     * falling back to a name a provider would reject or, worse, misread.
     */
    public Optional<UUID> locationId() {
        return Optional.ofNullable(locationId);
    }

    /**
     * Records which canonical location this city is — the administrative act
     * {@code V3__link_cities_to_canonical_locations.sql} describes but had no mechanism for.
     *
     * <p>Returns a new instance rather than mutating: the link is part of the city's identity for
     * translation purposes, and a setter would let a half-applied change escape into a sync that is
     * already running.
     *
     * <p>Re-linking an already-linked city is refused. Catalog trips are reconciled against the
     * provider ids that the old location resolved to, so silently repointing it would leave every
     * existing trip on this route mapped to a provider city this city no longer claims to be —
     * corruption that surfaces much later, as a booking against the wrong city. Unlinking is not
     * offered here for the same reason.
     */
    public City linkToCanonicalLocation(UUID canonicalLocationId) {
        Objects.requireNonNull(canonicalLocationId, "canonicalLocationId must not be null");
        if (locationId != null && !locationId.equals(canonicalLocationId)) {
            throw new IllegalStateException(
                    "City " + id.value() + " is already linked to canonical location " + locationId
                            + "; relinking would orphan every trip already synchronised under it");
        }
        return new City(id, name, state, country, canonicalLocationId);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public CityId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String state() {
        return state;
    }

    public String country() {
        return country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof City other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
