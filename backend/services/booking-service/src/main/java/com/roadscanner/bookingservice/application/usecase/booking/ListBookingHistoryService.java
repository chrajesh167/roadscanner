package com.roadscanner.bookingservice.application.usecase.booking;

import com.roadscanner.bookingservice.domain.port.in.ListBookingHistory;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;

import java.util.UUID;

/** Implements {@link ListBookingHistory}. {@code onBehalfOfTravelerId} is only honored for a
 * privileged requester — see the port's own Javadoc. */
public class ListBookingHistoryService implements ListBookingHistory {

    private final BookingRepository bookingRepository;

    public ListBookingHistoryService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Result list(Command command) {
        UUID travelerId = command.requester().isPrivileged() && command.onBehalfOfTravelerId().isPresent()
                ? command.onBehalfOfTravelerId().get()
                : command.requester().requesterId();
        return new Result(bookingRepository.findByTravelerId(travelerId));
    }
}
