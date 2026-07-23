package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.TripId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Composes {@code inventory-service}'s static {@code SeatLayout} with
 * {@code provider-integration-service}'s live per-seat status into one response —
 * {@code inventory-service}'s own {@code sequence-diagrams.md} flow 4 explicitly assigns this
 * composition to {@code booking-service}, not to {@code inventory-service} or the client
 * directly (docs/services/booking-service/use-cases.md).
 */
public interface GetSeatSelectionView {

    Result get(Command command);

    record Command(TripId tripId) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
        }
    }

    record Result(List<SeatView> seats) {
        public Result {
            Objects.requireNonNull(seats, "seats must not be null");
            seats = List.copyOf(seats);
        }
    }

    /** One seat's static shape (from {@code inventory-service}'s {@code SeatLayout}) merged with
     * its live status (from {@code provider-integration-service}'s seat map). */
    record SeatView(String seatNumber, String deck, String seatType, boolean wheelchairAccessible,
                     Integer position, String status, BigDecimal priceAmount, String priceCurrency) {
    }
}
