package com.roadscanner.bookingservice.application.usecase.hold;

import com.roadscanner.bookingservice.domain.exception.TripNotBookableException;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HoldSeats;
import com.roadscanner.bookingservice.domain.port.out.InventoryClient;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.testsupport.MutableClock;
import com.roadscanner.bookingservice.testsupport.fakes.InMemorySeatHoldRepository;
import com.roadscanner.bookingservice.testsupport.fakes.StubInventoryClient;
import com.roadscanner.bookingservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldSeatsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    private final StubInventoryClient inventoryClient = new StubInventoryClient();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final InMemorySeatHoldRepository seatHoldRepository = new InMemorySeatHoldRepository();
    private final MutableClock clock = new MutableClock(NOW);
    private final HoldSeatsService service =
            new HoldSeatsService(inventoryClient, providerIntegrationClient, seatHoldRepository, clock);

    private TripId bookableTrip() {
        TripId tripId = new TripId(UUID.randomUUID());
        inventoryClient.tripResult = id -> Optional.of(new InventoryClient.TripSnapshot(tripId, "Mumbai", "Pune",
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "Acme Travels", "AC Sleeper", List.of("WiFi"),
                BigDecimal.valueOf(500), "INR", true));
        inventoryClient.providerMappingResult = id -> Optional.of(
                new InventoryClient.ProviderMappingView(new ProviderType("MOCK"), "MOCK-TRIP-1"));
        return tripId;
    }

    @Test
    void holdsSeatsAndPersistsASeatHoldCapturingTripAndFareFacts() {
        TripId tripId = bookableTrip();

        HoldSeats.Result result = service.hold(new HoldSeats.Command(UUID.randomUUID(), tripId, List.of("L1")));

        assertThat(result.seatNumbers()).containsExactly("L1");
        assertThat(seatHoldRepository.findById(result.seatHoldId())).isPresent();
        assertThat(seatHoldRepository.findById(result.seatHoldId()).get().fare().amount())
                .isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void failsWhenTripHasNoProviderMapping() {
        TripId tripId = new TripId(UUID.randomUUID());
        inventoryClient.tripResult = id -> Optional.of(new InventoryClient.TripSnapshot(tripId, "Mumbai", "Pune",
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "Acme Travels", "AC Sleeper", List.of(),
                BigDecimal.valueOf(500), "INR", true));
        inventoryClient.providerMappingResult = id -> Optional.empty();

        assertThatThrownBy(() -> service.hold(new HoldSeats.Command(UUID.randomUUID(), tripId, List.of("L1"))))
                .isInstanceOf(TripNotBookableException.class);
    }

    @Test
    void failsWhenTripIsNotBookable() {
        TripId tripId = new TripId(UUID.randomUUID());
        inventoryClient.tripResult = id -> Optional.of(new InventoryClient.TripSnapshot(tripId, "Mumbai", "Pune",
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "Acme Travels", "AC Sleeper", List.of(),
                BigDecimal.valueOf(500), "INR", false));

        assertThatThrownBy(() -> service.hold(new HoldSeats.Command(UUID.randomUUID(), tripId, List.of("L1"))))
                .isInstanceOf(TripNotBookableException.class);
    }

    @Test
    void failsWhenTripDoesNotExist() {
        TripId tripId = new TripId(UUID.randomUUID());
        inventoryClient.tripResult = id -> Optional.empty();

        assertThatThrownBy(() -> service.hold(new HoldSeats.Command(UUID.randomUUID(), tripId, List.of("L1"))))
                .isInstanceOf(TripNotBookableException.class);
    }
}
