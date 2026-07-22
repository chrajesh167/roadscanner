package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.FareAmount;
import com.roadscanner.inventoryservice.domain.model.RouteId;
import com.roadscanner.inventoryservice.domain.model.SupplyOrigin;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.model.TripSchedule;

import java.util.Currency;
import java.util.List;

/** The only class in this package that sees both {@code domain.model} and {@link TripJpaEntity} —
 * matching {@code search-service}'s {@code SearchableTripMapper} convention exactly. */
final class TripMapper {

    private static final String AMENITIES_DELIMITER = ",";

    Trip toDomain(TripJpaEntity entity) {
        return Trip.reconstitute(
                new TripId(entity.getId()),
                entity.getRouteId() == null ? null : new RouteId(entity.getRouteId()),
                entity.getOrigin(),
                entity.getDestination(),
                new TripSchedule(entity.getDepartureTime(), entity.getArrivalTime()),
                entity.getOperatorId(),
                entity.getOperatorDisplayName(),
                entity.getBusId(),
                entity.getBusTypeCategory(),
                splitAmenities(entity.getAmenities()),
                new FareAmount(entity.getFareAmount(), Currency.getInstance(entity.getFareCurrency()), entity.getFareCapturedAt()),
                entity.isBookable(),
                SupplyOrigin.valueOf(entity.getSupplyOrigin()),
                entity.getCreatedAt(),
                entity.getLastEventAt()
        );
    }

    TripJpaEntity toNewEntity(Trip trip) {
        return new TripJpaEntity(
                trip.id().value(),
                trip.routeId().map(RouteId::value).orElse(null),
                trip.origin(),
                trip.destination(),
                trip.schedule().departureTime(),
                trip.schedule().arrivalTime(),
                trip.operatorId().orElse(null),
                trip.operatorDisplayName(),
                trip.busId().orElse(null),
                trip.busTypeCategory(),
                joinAmenities(trip.amenities()),
                trip.fare().amount(),
                trip.fare().currency().getCurrencyCode(),
                trip.fare().capturedAt(),
                trip.bookable(),
                trip.supplyOrigin().name(),
                trip.createdAt(),
                trip.lastEventAt()
        );
    }

    void applyTo(TripJpaEntity entity, Trip trip) {
        entity.applyMutableState(
                trip.routeId().map(RouteId::value).orElse(null),
                trip.origin(),
                trip.destination(),
                trip.schedule().departureTime(),
                trip.schedule().arrivalTime(),
                trip.operatorDisplayName(),
                trip.busTypeCategory(),
                joinAmenities(trip.amenities()),
                trip.fare().amount(),
                trip.fare().currency().getCurrencyCode(),
                trip.fare().capturedAt(),
                trip.bookable(),
                trip.lastEventAt()
        );
    }

    private String joinAmenities(List<String> amenities) {
        return String.join(AMENITIES_DELIMITER, amenities);
    }

    private List<String> splitAmenities(String amenities) {
        if (amenities == null || amenities.isBlank()) {
            return List.of();
        }
        return List.of(amenities.split(AMENITIES_DELIMITER));
    }
}
