package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.util.List;
import java.util.Objects;

/** Converts a still-{@code BLOCKED} seat hold into a confirmed booking with the provider.
 * Raises {@link com.roadscanner.providerintegrationservice.domain.exception.BookingFailedException}
 * if the provider declines (including a block that expired before this call arrived). */
public interface ConfirmBooking {

    Result confirm(Command command);

    /**
     * The trip is no longer passed in: it is read from the stored reservation the block reference
     * identifies. A caller-supplied trip id could disagree with the one the seats were actually
     * held against, and the provider would confirm the reservation regardless — booking someone
     * onto a departure nobody chose.
     */
    record Command(ProviderSessionId sessionId, String providerBlockReference, ContactDetail contact,
                    List<PassengerDetail> passengers) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (providerBlockReference == null || providerBlockReference.isBlank()) {
                throw new IllegalArgumentException("providerBlockReference must not be blank");
            }
            Objects.requireNonNull(contact, "contact must not be null");
            if (passengers == null || passengers.isEmpty()) {
                throw new IllegalArgumentException("passengers must not be empty");
            }
            passengers = List.copyOf(passengers);
        }
    }

    record Result(BookingConfirmation confirmation) {
        public Result {
            Objects.requireNonNull(confirmation, "confirmation must not be null");
        }
    }
}
