package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Re-validates the trip against {@code inventory-service}, then places a hold with
 * {@code provider-integration-service} and persists a local {@link
 * com.roadscanner.bookingservice.domain.model.SeatHold} — kept as a separate client-facing step
 * from {@code Create Booking}, deliberately, per FR-3.2's "temporary hold... during checkout"
 * (docs/services/booking-service/boundaries.md's "Why `Hold Seats` Is a Separate Client-Facing
 * Step").
 *
 * <p><strong>The hold now carries its passengers.</strong> A provider binds a seat to its occupant
 * at block time — FlixBus's seat-reservation call takes a gender per reserved seat so it can
 * enforce gender-restricted seats, and {@code provider-integration-service}'s
 * {@code BlockSeatRequest} takes the whole traveller. Passing only seat numbers, as this command
 * did, cannot express that and was rejected at the boundary.
 *
 * <p>The visible consequence is that the traveller supplies passenger details <em>before</em> the
 * hold rather than after. That is the provider's ordering, not a preference: the alternative is
 * holding seats against invented identities and correcting them at checkout, which would be
 * fabricating exactly the data this service refuses to fabricate.
 *
 * <p>Fails with {@code TripNotBookableException} for a trip with no {@code ProviderMapping} —
 * docs/services/booking-service/use-cases.md's "A Trip With No `ProviderMapping` Cannot Be
 * Held".
 */
public interface HoldSeats {

    Result hold(Command command);

    record Command(UUID travelerId, TripId tripId, List<Passenger> passengers) {
        public Command {
            Objects.requireNonNull(travelerId, "travelerId must not be null");
            Objects.requireNonNull(tripId, "tripId must not be null");
            if (passengers == null || passengers.isEmpty()) {
                throw new IllegalArgumentException("passengers must not be empty");
            }
            passengers = List.copyOf(passengers);
        }

        /** The seats these passengers occupy, in the order given. */
        public List<String> seatNumbers() {
            return passengers.stream().map(Passenger::seatNumber).toList();
        }
    }

    record Result(SeatHoldId seatHoldId, List<String> seatNumbers, Instant expiresAt) {
    }
}
