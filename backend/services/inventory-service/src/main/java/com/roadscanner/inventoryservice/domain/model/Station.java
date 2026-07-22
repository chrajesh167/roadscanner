package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.Optional;

/** A boarding/dropping point within a {@link City} — structured catalog geography, same
 * ownership posture as {@code City} (docs/services/inventory-service/domain-model.md). */
public final class Station {

    private final StationId id;
    private final CityId cityId;
    private String name;
    private StationType type;
    private Double latitude;
    private Double longitude;

    private Station(StationId id, CityId cityId, String name, StationType type, Double latitude, Double longitude) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.cityId = Objects.requireNonNull(cityId, "cityId must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Station create(StationId id, CityId cityId, String name, StationType type, Double latitude, Double longitude) {
        return new Station(id, cityId, name, type, latitude, longitude);
    }

    public static Station reconstitute(StationId id, CityId cityId, String name, StationType type, Double latitude, Double longitude) {
        return new Station(id, cityId, name, type, latitude, longitude);
    }

    public StationId id() {
        return id;
    }

    public CityId cityId() {
        return cityId;
    }

    public String name() {
        return name;
    }

    public StationType type() {
        return type;
    }

    public Optional<Double> latitude() {
        return Optional.ofNullable(latitude);
    }

    public Optional<Double> longitude() {
        return Optional.ofNullable(longitude);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Station other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
