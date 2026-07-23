package com.roadscanner.bookingservice.adapter.in.event;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentCompleted;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentFailed;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentTimedOut;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentEventListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final HandlePaymentCompleted handlePaymentCompleted = mock(HandlePaymentCompleted.class);
    private final HandlePaymentFailed handlePaymentFailed = mock(HandlePaymentFailed.class);
    private final HandlePaymentTimedOut handlePaymentTimedOut = mock(HandlePaymentTimedOut.class);
    private final PaymentEventListener listener =
            new PaymentEventListener(handlePaymentCompleted, handlePaymentFailed, handlePaymentTimedOut);

    @Test
    void completedDispatchesToHandlePaymentCompleted() {
        UUID bookingId = UUID.randomUUID();

        listener.onMessage(new PaymentEventMessage(PaymentEventType.COMPLETED, bookingId, "payment-ref-1", OCCURRED_AT));

        verify(handlePaymentCompleted).handle(
                new HandlePaymentCompleted.Command(new BookingId(bookingId), "payment-ref-1", OCCURRED_AT));
        verifyNoInteractions(handlePaymentFailed, handlePaymentTimedOut);
    }

    @Test
    void failedDispatchesToHandlePaymentFailed() {
        UUID bookingId = UUID.randomUUID();

        listener.onMessage(new PaymentEventMessage(PaymentEventType.FAILED, bookingId, "payment-ref-1", OCCURRED_AT));

        verify(handlePaymentFailed).handle(new HandlePaymentFailed.Command(new BookingId(bookingId), OCCURRED_AT));
        verifyNoInteractions(handlePaymentCompleted, handlePaymentTimedOut);
    }

    @Test
    void timedOutDispatchesToHandlePaymentTimedOut() {
        UUID bookingId = UUID.randomUUID();

        listener.onMessage(new PaymentEventMessage(PaymentEventType.TIMED_OUT, bookingId, "payment-ref-1", OCCURRED_AT));

        verify(handlePaymentTimedOut).handle(new HandlePaymentTimedOut.Command(new BookingId(bookingId), OCCURRED_AT));
        verifyNoInteractions(handlePaymentCompleted, handlePaymentFailed);
    }
}
