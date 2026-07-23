package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.RequesterContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Every booking a traveler has ever made, regardless of status (FR-1.3) — nothing is ever
 * excluded (docs/services/booking-service/data-ownership.md's "Retention"). */
public interface ListBookingHistory {

    Result list(Command command);

    /** {@code onBehalfOfTravelerId} is only honored for an {@code ADMIN}/{@code SUPPORT}
     * requester (FR-8.3's support-lookup journey); for a {@code TRAVELER} requester it is
     * ignored and the requester's own history is returned regardless of what's passed —
     * enforced by the application layer, not merely by convention. */
    record Command(RequesterContext requester, Optional<UUID> onBehalfOfTravelerId) {
        public Command {
            Objects.requireNonNull(requester, "requester must not be null");
            onBehalfOfTravelerId = onBehalfOfTravelerId == null ? Optional.empty() : onBehalfOfTravelerId;
        }
    }

    record Result(List<Booking> bookings) {
    }
}
