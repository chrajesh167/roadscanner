package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.Optional;

/** A structured, city-to-city route definition — distinct from the plain origin/destination
 * display strings every {@link Trip} and published event also carries (kept for compatibility
 * with {@code search-service}'s existing string-based model — see
 * docs/services/inventory-service/domain-model.md's compatibility note). Used to define trips
 * and to drive catalog synchronization (this service searches each provider *by* route). */
public final class Route {

    private final RouteId id;
    private final CityId originCityId;
    private final CityId destinationCityId;
    private Double distanceKm;

    private Route(RouteId id, CityId originCityId, CityId destinationCityId, Double distanceKm) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.originCityId = Objects.requireNonNull(originCityId, "originCityId must not be null");
        this.destinationCityId = Objects.requireNonNull(destinationCityId, "destinationCityId must not be null");
        if (originCityId.equals(destinationCityId)) {
            throw new IllegalArgumentException("originCityId and destinationCityId must differ");
        }
        this.distanceKm = distanceKm;
    }

    public static Route create(RouteId id, CityId originCityId, CityId destinationCityId, Double distanceKm) {
        return new Route(id, originCityId, destinationCityId, distanceKm);
    }

    public static Route reconstitute(RouteId id, CityId originCityId, CityId destinationCityId, Double distanceKm) {
        return new Route(id, originCityId, destinationCityId, distanceKm);
    }

    public RouteId id() {
        return id;
    }

    public CityId originCityId() {
        return originCityId;
    }

    public CityId destinationCityId() {
        return destinationCityId;
    }

    public Optional<Double> distanceKm() {
        return Optional.ofNullable(distanceKm);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Route other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
