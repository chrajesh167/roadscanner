package com.roadscanner.bookingservice.application.usecase.hold;

import com.roadscanner.bookingservice.domain.exception.SeatHoldNotFoundException;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.ReleaseHold;
import com.roadscanner.bookingservice.testsupport.fakes.InMemorySeatHoldRepository;
import com.roadscanner.bookingservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseHoldServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemorySeatHoldRepository seatHoldRepository = new InMemorySeatHoldRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final ReleaseHoldService service = new ReleaseHoldService(seatHoldRepository, providerIntegrationClient);

    @Test
    void releasesAnOwnedHoldAndDeletesIt() {
        UUID travelerId = UUID.randomUUID();
        SeatHold hold = SeatHold.create(SeatHoldId.generate(), travelerId, new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", List.of("L1"),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0.plusSeconds(600), T0);
        seatHoldRepository.save(hold);

        ReleaseHold.Result result = service.release(new ReleaseHold.Command(travelerId, hold.id()));

        assertThat(result.released()).isTrue();
        assertThat(seatHoldRepository.findById(hold.id())).isEmpty();
        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(1);
    }

    @Test
    void failsForAHoldOwnedBySomeoneElse() {
        SeatHold hold = SeatHold.create(SeatHoldId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", List.of("L1"),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0.plusSeconds(600), T0);
        seatHoldRepository.save(hold);

        assertThatThrownBy(() -> service.release(new ReleaseHold.Command(UUID.randomUUID(), hold.id())))
                .isInstanceOf(SeatHoldNotFoundException.class);
    }

    @Test
    void failsForAnUnknownHold() {
        assertThatThrownBy(() -> service.release(new ReleaseHold.Command(UUID.randomUUID(), SeatHoldId.generate())))
                .isInstanceOf(SeatHoldNotFoundException.class);
    }
}
