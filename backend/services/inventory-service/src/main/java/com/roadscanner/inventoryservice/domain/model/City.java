package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;

/** Structured catalog geography — new to the platform, per docs/services/inventory-service/domain-model.md.
 * Owned outright by this service, kept current via administrative catalog management, not
 * event-driven (there is no upstream "City" concept anywhere else on the platform). */
public final class City {

    private final CityId id;
    private String name;
    private String state;
    private String country;

    private City(CityId id, String name, String state, String country) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = requireNonBlank(name, "name");
        this.state = requireNonBlank(state, "state");
        this.country = requireNonBlank(country, "country");
    }

    public static City create(CityId id, String name, String state, String country) {
        return new City(id, name, state, country);
    }

    public static City reconstitute(CityId id, String name, String state, String country) {
        return new City(id, name, state, country);
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
