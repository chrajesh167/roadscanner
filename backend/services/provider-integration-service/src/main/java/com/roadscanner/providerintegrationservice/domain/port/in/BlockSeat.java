package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;

import java.util.List;
import java.util.Objects;

/** Places a temporary hold on one or more seats with the provider. Raises
 * {@link com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException}
 * if any named seat is no longer available. */
public interface BlockSeat {

    Result block(Command command);

    /**
     * Passengers rather than bare seat numbers: a hold binds a seat to the person taking it, and
     * providers need the occupant to honour gender-restricted seats. The seat is carried on each
     * passenger, so no parallel list can fall out of step.
     */
    record Command(ProviderSessionId sessionId, String providerTripId, List<PassengerDetail> passengers) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (providerTripId == null || providerTripId.isBlank()) {
                throw new IllegalArgumentException("providerTripId must not be blank");
            }
            if (passengers == null || passengers.isEmpty()) {
                throw new IllegalArgumentException("passengers must not be empty");
            }
            passengers = List.copyOf(passengers);
        }
    }

    record Result(SeatReservation reservation) {
        public Result {
            Objects.requireNonNull(reservation, "reservation must not be null");
        }
    }
}
