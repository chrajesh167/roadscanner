package com.roadscanner.providerintegrationservice.application.usecase.seatmap;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatStatus;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.port.in.GetSeatMap;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;
import com.roadscanner.providerintegrationservice.testsupport.MutableClock;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderCache;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemorySessionRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.StubProviderClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GetSeatMapServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void cachesTheSeatMapOnFirstFetchAndServesTheSecondCallFromCacheWithoutCallingTheProviderAgain() {
        MutableClock clock = new MutableClock(NOW);
        InMemorySessionRepository sessionRepository = new InMemorySessionRepository();
        ProviderSessionId sessionId = ProviderSessionId.generate();
        sessionRepository.save(ProviderSession.open(sessionId, ProviderType.MOCK,
                new ProviderToken("access", null, "Bearer", NOW.plusSeconds(3600)), NOW));

        StubProviderClient client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEAT_MAP));
        ProviderSeat seat = new ProviderSeat(new SeatNumber("L1"), "LOWER", "AC Sleeper", SeatStatus.AVAILABLE,
                new FareAmount(BigDecimal.valueOf(500), Currency.getInstance("INR")));
        ProviderSeatMap seatMap = new ProviderSeatMap("TRIP-1", ProviderType.MOCK, List.of(seat));
        client.seatMapResult = () -> seatMap;

        InMemoryProviderCache cache = new InMemoryProviderCache();
        GetSeatMap service = new GetSeatMapService(new ActiveSessionResolver(sessionRepository, clock),
                new ProviderClientRegistry(List.of(client)), cache);

        GetSeatMap.Command command = new GetSeatMap.Command(sessionId, "TRIP-1");
        GetSeatMap.Result first = service.getSeatMap(command);
        GetSeatMap.Result second = service.getSeatMap(command);

        assertThat(first.seatMap()).isEqualTo(seatMap);
        assertThat(second.seatMap()).isEqualTo(seatMap);
        assertThat(client.seatMapCallCount).isEqualTo(1);
    }
}
