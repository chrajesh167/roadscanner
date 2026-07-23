package com.roadscanner.bookingservice.application.usecase.ticket;

import com.roadscanner.bookingservice.domain.exception.BookingNotFoundException;
import com.roadscanner.bookingservice.domain.exception.TicketNotAvailableException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.port.in.GetTicket;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;

/** Implements {@link GetTicket} — returns the ticket already persisted at confirmation time,
 * never re-asks {@code provider-integration-service} (FR-3.6). */
public class GetTicketService implements GetTicket {

    private final BookingRepository bookingRepository;

    public GetTicketService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Result get(Command command) {
        Booking booking = bookingRepository.findById(command.bookingId())
                .filter(b -> canView(b, command.requester()))
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));
        Ticket ticket = booking.ticket().orElseThrow(() -> new TicketNotAvailableException(booking.id()));
        return new Result(ticket);
    }

    private boolean canView(Booking booking, RequesterContext requester) {
        return requester.isPrivileged() || booking.isOwnedBy(requester.requesterId());
    }
}
