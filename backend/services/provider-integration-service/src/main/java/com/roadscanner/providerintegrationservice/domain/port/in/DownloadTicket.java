package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;

import java.util.Objects;

/** Fetches the ticket document for a confirmed booking. Raises
 * {@link com.roadscanner.providerintegrationservice.domain.exception.TicketNotFoundException} if
 * the provider has none on file. */
public interface DownloadTicket {

    Result download(Command command);

    record Command(ProviderSessionId sessionId, BookingReference bookingReference) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        }
    }

    record Result(ProviderTicket ticket) {
        public Result {
            Objects.requireNonNull(ticket, "ticket must not be null");
        }
    }
}
