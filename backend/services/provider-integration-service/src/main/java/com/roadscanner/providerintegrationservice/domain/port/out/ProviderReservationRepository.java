package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;

import java.util.Optional;

/**
 * Stores provider-side holds so a booking confirmed minutes after a block still has the exact
 * identifiers the provider issued. Nothing recovers them once lost — see
 * {@link SeatReservation}'s Javadoc.
 */
public interface ProviderReservationRepository {

    SeatReservation save(SeatReservation reservation);

    Optional<SeatReservation> findById(ReservationId reservationId);

    /** Looked up by the provider's own handle, which is what a caller holds after a block. */
    Optional<SeatReservation> findByBlockReference(ProviderType providerType, String providerBlockReference);
}
