package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;

/**
 * The only class in this package that sees both {@code location.domain.model} and
 * {@link LocationJpaEntity} — the same single-bridge rule {@code SearchableTripMapper} follows.
 * Stateless; owned as a plain field by the adapter rather than injected.
 */
final class LocationMapper {

    Location toDomain(LocationJpaEntity entity) {
        return Location.reconstitute(
                new LocationId(entity.getId()),
                entity.getDisplayName(),
                new LocationAddress(entity.getCity(), entity.getState(), entity.getCountry()),
                GeoCoordinates.ofNullable(entity.getLatitude(), entity.getLongitude()),
                GooglePlaceId.ofNullable(entity.getGooglePlaceId()),
                entity.getTimezone(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    LocationJpaEntity toEntity(Location location) {
        GeoCoordinates coordinates = location.coordinates().orElse(null);
        return new LocationJpaEntity(
                location.id().value(),
                location.displayName(),
                location.address().city(),
                location.address().state(),
                location.address().country(),
                coordinates == null ? null : coordinates.latitude(),
                coordinates == null ? null : coordinates.longitude(),
                location.googlePlaceId().map(GooglePlaceId::value).orElse(null),
                location.timezone().orElse(null),
                location.isActive(),
                location.createdAt(),
                location.updatedAt());
    }

    /**
     * Copies mutable state onto an entity already managed by the persistence context, so an
     * update is a dirty-check on the loaded row rather than a fresh insert-or-merge. Same
     * fetch-then-mutate reasoning as {@code SearchableTripRepositoryAdapter.save}.
     */
    void applyTo(LocationJpaEntity entity, Location location) {
        GeoCoordinates coordinates = location.coordinates().orElse(null);
        entity.setDisplayName(location.displayName());
        entity.setCity(location.address().city());
        entity.setState(location.address().state());
        entity.setCountry(location.address().country());
        entity.setLatitude(coordinates == null ? null : coordinates.latitude());
        entity.setLongitude(coordinates == null ? null : coordinates.longitude());
        entity.setGooglePlaceId(location.googlePlaceId().map(GooglePlaceId::value).orElse(null));
        entity.setTimezone(location.timezone().orElse(null));
        entity.setActive(location.isActive());
        entity.setUpdatedAt(location.updatedAt());
    }
}
