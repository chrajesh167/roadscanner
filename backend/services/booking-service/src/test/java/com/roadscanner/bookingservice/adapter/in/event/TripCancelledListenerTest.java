package com.roadscanner.bookingservice.adapter.in.event;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HandleTripCancelled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Verifies {@link TripCancelledListener} dispatches only {@code CANCELLED}, ignoring
 * {@code PUBLISHED}/{@code UPDATED} on the same topic
 * (docs/services/booking-service/events-consumed.md). */
class TripCancelledListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final HandleTripCancelled handleTripCancelled = mock(HandleTripCancelled.class);
    private final TripCancelledListener listener = new TripCancelledListener(handleTripCancelled);

    private CatalogTripEventMessage message(CatalogTripEventType eventType, UUID tripId) {
        return new CatalogTripEventMessage(eventType, tripId, UUID.randomUUID(), "Acme Travels", "Mumbai", "Pune",
                OCCURRED_AT.plusSeconds(3600), OCCURRED_AT.plusSeconds(7200), "AC Sleeper", List.of("WiFi"),
                java.math.BigDecimal.valueOf(500), "INR", OCCURRED_AT);
    }

    @Test
    void cancelledDispatchesToHandleTripCancelled() {
        UUID tripId = UUID.randomUUID();

        listener.onMessage(message(CatalogTripEventType.CANCELLED, tripId));

        verify(handleTripCancelled).handle(new HandleTripCancelled.Command(new TripId(tripId), OCCURRED_AT));
    }

    @Test
    void publishedIsIgnored() {
        listener.onMessage(message(CatalogTripEventType.PUBLISHED, UUID.randomUUID()));

        verifyNoInteractions(handleTripCancelled);
    }

    @Test
    void updatedIsIgnored() {
        listener.onMessage(message(CatalogTripEventType.UPDATED, UUID.randomUUID()));

        verifyNoInteractions(handleTripCancelled);
    }
}
