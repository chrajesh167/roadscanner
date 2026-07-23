package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Ticket;

import java.util.Objects;

/** Returns the {@link Ticket} already persisted on the booking at confirmation time — never
 * re-asks {@code provider-integration-service} (FR-3.6, docs/services/booking-service/data-ownership.md).
 * Throws {@code TicketNotAvailableException} for anything not yet {@code CONFIRMED}/{@code COMPLETED}. */
public interface GetTicket {

    Result get(Command command);

    record Command(BookingId bookingId, RequesterContext requester) {
        public Command {
            Objects.requireNonNull(bookingId, "bookingId must not be null");
            Objects.requireNonNull(requester, "requester must not be null");
        }
    }

    record Result(Ticket ticket) {
    }
}
