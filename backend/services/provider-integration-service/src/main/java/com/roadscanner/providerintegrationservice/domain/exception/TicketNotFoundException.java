package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.BookingReference;

/** The provider has no ticket on file for the given {@link BookingReference} — either it was
 * never confirmed, or the provider hasn't generated the ticket document yet. */
public class TicketNotFoundException extends ProviderIntegrationException {

    private final BookingReference bookingReference;

    public TicketNotFoundException(BookingReference bookingReference) {
        super("No ticket found for booking reference: " + bookingReference, null);
        this.bookingReference = bookingReference;
    }

    public BookingReference bookingReference() {
        return bookingReference;
    }
}
