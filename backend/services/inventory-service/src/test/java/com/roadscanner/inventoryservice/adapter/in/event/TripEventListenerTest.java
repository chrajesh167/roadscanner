package com.roadscanner.inventoryservice.adapter.in.event;

import com.roadscanner.inventoryservice.domain.port.in.IngestCancelledTrip;
import com.roadscanner.inventoryservice.domain.port.in.IngestPublishedTrip;
import com.roadscanner.inventoryservice.domain.port.in.IngestUpdatedTrip;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Verifies {@link TripEventListener} dispatches each {@link OperatorTripEventType} to the
 * correct inbound port with a correctly-translated command — the mapping layer between the
 * Kafka wire shape and the domain ports, exercised here without a broker (no Testcontainers
 * needed for dispatch-logic correctness; the wire format itself is covered separately by
 * {@code CatalogTripEventMessageShapeTest} on the outbound side). */
class TripEventListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-01T00:00:00Z");

    private final IngestPublishedTrip ingestPublishedTrip = mock(IngestPublishedTrip.class);
    private final IngestUpdatedTrip ingestUpdatedTrip = mock(IngestUpdatedTrip.class);
    private final IngestCancelledTrip ingestCancelledTrip = mock(IngestCancelledTrip.class);
    private final TripEventListener listener =
            new TripEventListener(ingestPublishedTrip, ingestUpdatedTrip, ingestCancelledTrip);

    @Test
    void publishedDispatchesToIngestPublishedTripWithSeatLayoutTranslated() {
        UUID tripId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        OperatorTripEventMessage message = new OperatorTripEventMessage(OperatorTripEventType.PUBLISHED, tripId,
                operatorId, "Acme Travels", "Mumbai", "Pune", OCCURRED_AT.plusSeconds(3600),
                OCCURRED_AT.plusSeconds(7200), "AC Sleeper", List.of("WiFi"), BigDecimal.valueOf(500), "INR",
                List.of(new SeatEntryMessage("L1", "LOWER", "SLEEPER", false, 1)), OCCURRED_AT);

        listener.onMessage(message);

        verify(ingestPublishedTrip).ingest(new IngestPublishedTrip.Command(tripId, operatorId, "Acme Travels",
                "Mumbai", "Pune", OCCURRED_AT.plusSeconds(3600), OCCURRED_AT.plusSeconds(7200), "AC Sleeper",
                List.of("WiFi"), BigDecimal.valueOf(500), "INR",
                List.of(new IngestPublishedTrip.SeatEntry("L1", "LOWER", "SLEEPER", false, 1)), OCCURRED_AT));
        verifyNoInteractions(ingestUpdatedTrip, ingestCancelledTrip);
    }

    @Test
    void updatedDispatchesToIngestUpdatedTrip() {
        UUID tripId = UUID.randomUUID();
        OperatorTripEventMessage message = new OperatorTripEventMessage(OperatorTripEventType.UPDATED, tripId,
                UUID.randomUUID(), "Acme Travels", "Mumbai", "Pune", OCCURRED_AT.plusSeconds(3600),
                OCCURRED_AT.plusSeconds(7200), "AC Sleeper", List.of(), BigDecimal.valueOf(550), "INR",
                List.of(), OCCURRED_AT);

        listener.onMessage(message);

        verify(ingestUpdatedTrip).ingest(new IngestUpdatedTrip.Command(tripId, "Mumbai", "Pune",
                OCCURRED_AT.plusSeconds(3600), OCCURRED_AT.plusSeconds(7200), "Acme Travels", "AC Sleeper",
                List.of(), BigDecimal.valueOf(550), "INR", OCCURRED_AT));
        verifyNoInteractions(ingestPublishedTrip, ingestCancelledTrip);
    }

    @Test
    void cancelledDispatchesToIngestCancelledTripWithOnlyIdAndTimestamp() {
        UUID tripId = UUID.randomUUID();
        OperatorTripEventMessage message = new OperatorTripEventMessage(OperatorTripEventType.CANCELLED, tripId,
                null, null, null, null, null, null, null, null, null, null, List.of(), OCCURRED_AT);

        listener.onMessage(message);

        verify(ingestCancelledTrip).ingest(new IngestCancelledTrip.Command(tripId, OCCURRED_AT));
        verifyNoInteractions(ingestPublishedTrip, ingestUpdatedTrip);
    }
}
