package com.roadscanner.searchservice.adapter.out.persistence;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.RatingSnapshot;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;

import java.util.Currency;
import java.util.List;

/**
 * The only class in this package that sees both {@code domain.model} and the JPA entity —
 * matching {@code auth-service}'s {@code CredentialMapper} convention exactly.
 * {@link SearchableTripRepositoryAdapter} also imports {@code domain.model} (its port
 * signatures require it) but never itself reads an entity field to build a domain object or
 * vice versa; it delegates every such conversion here. {@link SearchableTripSpecifications}
 * stays on the opposite side of that same line — it builds queries from primitive/JPA-safe
 * values only, never a domain type. Stateless by design; owned as a plain field by
 * {@link SearchableTripRepositoryAdapter} rather than injected, since it has no dependencies of
 * its own.
 */
final class SearchableTripMapper {

    private static final String AMENITIES_DELIMITER = ",";

    SearchableTrip toDomain(SearchableTripJpaEntity entity) {
        return SearchableTrip.reconstitute(
                new TripId(entity.getTripId()),
                new OperatorId(entity.getOperatorId()),
                entity.getOperatorName(),
                new Route(entity.getOrigin(), entity.getDestination()),
                new Schedule(entity.getDepartureTime(), entity.getArrivalTime()),
                new BusType(entity.getBusTypeCategory(), splitAmenities(entity.getAmenities())),
                new FareSnapshot(entity.getFareAmount(), Currency.getInstance(entity.getFareCurrency())),
                entity.isBookable(),
                new RatingSnapshot(entity.getRatingAverage(), entity.getRatingReviewCount()),
                entity.getCreatedAt(),
                entity.getLastTripEventAt(),
                entity.getLastRatingEventAt()
        );
    }

    SearchableTripJpaEntity toNewEntity(SearchableTrip trip) {
        return new SearchableTripJpaEntity(
                trip.tripId().value(),
                trip.operatorId().value(),
                trip.operatorName(),
                trip.route().origin(),
                trip.route().destination(),
                trip.schedule().departureTime(),
                trip.schedule().arrivalTime(),
                trip.busType().category(),
                joinAmenities(trip.busType().amenities()),
                trip.fare().amount(),
                trip.fare().currency().getCurrencyCode(),
                trip.bookable(),
                trip.rating().average(),
                trip.rating().reviewCount(),
                trip.createdAt(),
                trip.lastTripEventAt(),
                trip.lastRatingEventAt()
        );
    }

    /** Updates an already-managed entity in place — see {@link SearchableTripRepositoryAdapter}
     * for why this is distinct from {@link #toNewEntity}. */
    void applyTo(SearchableTripJpaEntity entity, SearchableTrip trip) {
        entity.applyMutableState(
                trip.operatorName(),
                trip.route().origin(),
                trip.route().destination(),
                trip.schedule().departureTime(),
                trip.schedule().arrivalTime(),
                trip.busType().category(),
                joinAmenities(trip.busType().amenities()),
                trip.fare().amount(),
                trip.fare().currency().getCurrencyCode(),
                trip.bookable(),
                trip.rating().average(),
                trip.rating().reviewCount(),
                trip.lastTripEventAt(),
                trip.lastRatingEventAt()
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
