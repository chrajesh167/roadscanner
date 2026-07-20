package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;

import java.util.List;
import java.util.Objects;

/** Places a temporary hold on one or more seats with the provider. Raises
 * {@link com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException}
 * if any named seat is no longer available. */
public interface BlockSeat {

    Result block(Command command);

    record Command(ProviderSessionId sessionId, String providerTripId, List<SeatNumber> seatNumbers) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (providerTripId == null || providerTripId.isBlank()) {
                throw new IllegalArgumentException("providerTripId must not be blank");
            }
            if (seatNumbers == null || seatNumbers.isEmpty()) {
                throw new IllegalArgumentException("seatNumbers must not be empty");
            }
            seatNumbers = List.copyOf(seatNumbers);
        }
    }

    record Result(SeatReservation reservation) {
        public Result {
            Objects.requireNonNull(reservation, "reservation must not be null");
        }
    }
}
