package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link SeatHold} — deliberately transient (docs/services/booking-service/
 * data-ownership.md), unlike {@link BookingRepository}'s permanent records. */
public interface SeatHoldRepository {

    SeatHold save(SeatHold seatHold);

    Optional<SeatHold> findById(SeatHoldId id);

    Optional<SeatHold> findByProviderBlockReference(String providerBlockReference);

    void deleteById(SeatHoldId id);

    /** Backs {@code Sweep Stale Holds}. */
    List<SeatHold> findAllExpiredBefore(Instant cutoff);
}
