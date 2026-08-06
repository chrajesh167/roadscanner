package com.roadscanner.bookingservice.adapter.out.persistence;

import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;

import java.util.Currency;

final class SeatHoldMapper {

    SeatHold toDomain(SeatHoldJpaEntity entity) {
        return SeatHold.reconstitute(
                new SeatHoldId(entity.getId()),
                entity.getTravelerId(),
                new TripId(entity.getTripId()),
                entity.getTripDepartureTime(),
                new ProviderType(entity.getProviderType()),
                entity.getProviderTripId(),
                entity.getProviderBlockReference(),
                entity.getPassengers().stream()
                        .map(p -> new Passenger(p.getFirstName(), p.getLastName(), p.getBirthDate(), p.getGender(),
                                p.getSeatNumber()))
                        .toList(),
                new Fare(entity.getFareAmount(), Currency.getInstance(entity.getFareCurrency())),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }

    SeatHoldJpaEntity toNewEntity(SeatHold hold) {
        return new SeatHoldJpaEntity(
                hold.id().value(),
                hold.travelerId(),
                hold.tripId().value(),
                hold.tripDepartureTime(),
                hold.providerType().code(),
                hold.providerTripId(),
                hold.providerBlockReference(),
                hold.passengers().stream()
                        .map(p -> new PassengerEmbeddable(p.firstName(), p.lastName(), p.birthDate(), p.gender(),
                                p.seatNumber()))
                        .toList(),
                hold.fare().amount(),
                hold.fare().currency().getCurrencyCode(),
                hold.expiresAt(),
                hold.createdAt()
        );
    }
}
