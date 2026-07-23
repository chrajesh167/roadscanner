package com.roadscanner.bookingservice.adapter.in.event;

import com.roadscanner.bookingservice.domain.port.in.HandleSeatReleased;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SeatReleasedListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final HandleSeatReleased handleSeatReleased = mock(HandleSeatReleased.class);
    private final SeatReleasedListener listener = new SeatReleasedListener(handleSeatReleased);

    @Test
    void seatReleasedDispatchesToHandleSeatReleased() {
        listener.onMessage(new ProviderAuditMessage("SeatReleased", "MOCK", "block-ref-1", OCCURRED_AT));

        verify(handleSeatReleased).handle(new HandleSeatReleased.Command("block-ref-1", OCCURRED_AT));
    }

    @Test
    void otherAuditEventTypesAreIgnored() {
        listener.onMessage(new ProviderAuditMessage("ProviderUnavailable", "MOCK", null, OCCURRED_AT));
        listener.onMessage(new ProviderAuditMessage("SessionExpired", "MOCK", null, OCCURRED_AT));

        verifyNoInteractions(handleSeatReleased);
    }
}
