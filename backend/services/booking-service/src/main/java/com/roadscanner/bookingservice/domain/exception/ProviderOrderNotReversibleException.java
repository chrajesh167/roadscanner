package com.roadscanner.bookingservice.domain.exception;

import com.roadscanner.bookingservice.domain.model.BookingId;

/**
 * A confirmed booking reached the provider, but this service cannot name the order to reverse it.
 *
 * <p>Only reachable for bookings confirmed before {@code provider_order_reference} was recorded
 * (V3). Their order reference exists at the provider and nowhere here, and it cannot be derived —
 * the booking reference is a different identifier the cancel route does not accept.
 *
 * <p>Raised rather than skipped, deliberately. Continuing would cancel the booking locally and
 * refund the traveller while the provider still holds a live, paid order — the platform pays for a
 * journey nobody takes, and nothing surfaces it. Failing here is visible and recoverable: support
 * can cancel the order directly with the provider.
 */
public class ProviderOrderNotReversibleException extends BookingServiceException {

    private final BookingId bookingId;

    public ProviderOrderNotReversibleException(BookingId bookingId) {
        super("Booking " + bookingId + " was confirmed with the provider but has no recorded provider "
                + "order reference, so its order cannot be reversed");
        this.bookingId = bookingId;
    }

    public BookingId bookingId() {
        return bookingId;
    }
}
