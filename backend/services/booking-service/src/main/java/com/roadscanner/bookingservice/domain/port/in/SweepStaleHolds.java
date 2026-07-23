package com.roadscanner.bookingservice.domain.port.in;

/**
 * Defensive, secondary sweep (docs/services/booking-service/use-cases.md) — deletes any
 * {@code SeatHold} past its {@code expiresAt} that {@code Handle Seat Released} hasn't already
 * cleaned up, and cancels any {@code PENDING_PAYMENT} booking whose held reservation has expired.
 * A safety net only, given {@code SeatReleased} is not yet published by
 * {@code provider-integration-service} — not needed for correctness, only for hygiene and
 * traveler-facing timeliness.
 */
public interface SweepStaleHolds {

    Result sweep();

    record Result(int expiredHoldsRemoved, int bookingsCancelled) {
    }
}
