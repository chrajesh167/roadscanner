package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.util.Objects;

/** Releases a hold placed by {@link BlockSeat} — {@code providerBlockReference} is the value
 * returned in {@code BlockSeat.Result}'s {@code SeatReservation.providerBlockReference()}. A
 * repeat call for an already-released block is a no-op, not an error (matching
 * {@code SeatReservation.release()}'s idempotent-transition contract). */
public interface ReleaseSeat {

    Result release(Command command);

    record Command(ProviderSessionId sessionId, String providerBlockReference) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (providerBlockReference == null || providerBlockReference.isBlank()) {
                throw new IllegalArgumentException("providerBlockReference must not be blank");
            }
        }
    }

    record Result(boolean released) {
    }
}
