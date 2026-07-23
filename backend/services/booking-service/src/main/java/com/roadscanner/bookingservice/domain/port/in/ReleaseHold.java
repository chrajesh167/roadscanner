package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.SeatHoldId;

import java.util.Objects;
import java.util.UUID;

/** Voluntary release before booking (FR-3.4's explicit-abandonment path) — idempotent, matching
 * {@code provider-integration-service}'s own {@code ReleaseSeat} idempotency. */
public interface ReleaseHold {

    Result release(Command command);

    record Command(UUID travelerId, SeatHoldId seatHoldId) {
        public Command {
            Objects.requireNonNull(travelerId, "travelerId must not be null");
            Objects.requireNonNull(seatHoldId, "seatHoldId must not be null");
        }
    }

    record Result(boolean released) {
    }
}
