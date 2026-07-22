package com.roadscanner.inventoryservice.application.usecase.ingestion;

import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.IngestPublishedTrip;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryOperatorRefRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemorySeatLayoutRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryTripRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.RecordingCatalogEventPublisher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IngestPublishedTripServiceTest {

    private final InMemoryTripRepository tripRepository = new InMemoryTripRepository();
    private final InMemorySeatLayoutRepository seatLayoutRepository = new InMemorySeatLayoutRepository();
    private final InMemoryOperatorRefRepository operatorRefRepository = new InMemoryOperatorRefRepository();
    private final RecordingCatalogEventPublisher catalogEventPublisher = new RecordingCatalogEventPublisher();
    private final IngestPublishedTripService service = new IngestPublishedTripService(
            tripRepository, seatLayoutRepository, operatorRefRepository, catalogEventPublisher);

    @Test
    void ingestsTripAndSeatLayoutAndPublishesTripPublished() {
        UUID tripId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-01T00:00:00Z");

        service.ingest(new IngestPublishedTrip.Command(tripId, UUID.randomUUID(), "Acme Travels", "Mumbai", "Pune",
                now.plusSeconds(3600), now.plusSeconds(7200), "AC Sleeper", List.of("WiFi"),
                BigDecimal.valueOf(500), "INR",
                List.of(new IngestPublishedTrip.SeatEntry("L1", "LOWER", "SLEEPER", false, 1)), now));

        assertThat(tripRepository.findById(new TripId(tripId))).isPresent();
        assertThat(seatLayoutRepository.findByTripId(new TripId(tripId))).isPresent();
        assertThat(seatLayoutRepository.findByTripId(new TripId(tripId)).get().seatCount()).isEqualTo(1);
        assertThat(catalogEventPublisher.tripEvents()).hasSize(1);
        assertThat(catalogEventPublisher.tripEvents().get(0).eventType()).isEqualTo("TripPublished");
    }

    @Test
    void seedsOperatorRefOnFirstSighting() {
        UUID operatorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-01T00:00:00Z");

        service.ingest(new IngestPublishedTrip.Command(UUID.randomUUID(), operatorId, "Acme Travels", "Mumbai",
                "Pune", now.plusSeconds(3600), now.plusSeconds(7200), "AC Sleeper", List.of(),
                BigDecimal.valueOf(500), "INR",
                List.of(new IngestPublishedTrip.SeatEntry("L1", "LOWER", "SLEEPER", false, 1)), now));

        assertThat(operatorRefRepository.findById(operatorId)).isPresent();
        assertThat(operatorRefRepository.findById(operatorId).get().displayName()).isEqualTo("Acme Travels");
    }
}
