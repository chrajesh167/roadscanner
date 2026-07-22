package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.Seat;
import com.roadscanner.inventoryservice.domain.model.SeatLayout;
import com.roadscanner.inventoryservice.domain.model.SeatNumber;
import com.roadscanner.inventoryservice.domain.model.TripId;

final class SeatLayoutMapper {

    SeatLayout toDomain(SeatLayoutJpaEntity entity) {
        return new SeatLayout(new TripId(entity.getTripId()), entity.getSeats().stream()
                .map(s -> new Seat(new SeatNumber(s.getSeatNumber()), s.getDeck(), s.getSeatType(),
                        s.isWheelchairAccessible(), s.getPosition()))
                .toList());
    }

    SeatLayoutJpaEntity toNewEntity(SeatLayout seatLayout) {
        return new SeatLayoutJpaEntity(seatLayout.tripId().value(), seatLayout.seats().stream()
                .map(s -> new SeatEmbeddable(s.seatNumber().value(), s.deck(), s.seatType(),
                        s.wheelchairAccessible(), s.position()))
                .toList());
    }
}
