package com.roadscanner.providerintegrationservice.application.usecase.search;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchTrips;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;
import com.roadscanner.providerintegrationservice.testsupport.MutableClock;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemorySessionRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.StubProviderClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTripsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void delegatesToTheResolvedProviderClientForTheSessionsProvider() {
        MutableClock clock = new MutableClock(NOW);
        InMemorySessionRepository sessionRepository = new InMemorySessionRepository();
        ProviderSessionId sessionId = ProviderSessionId.generate();
        sessionRepository.save(ProviderSession.open(sessionId, ProviderType.MOCK,
                new ProviderToken("access", null, "Bearer", NOW.plusSeconds(3600)), NOW));

        StubProviderClient client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEARCH));
        ProviderTrip trip = new ProviderTrip("TRIP-1", ProviderType.MOCK, "Mock Travels", "Mumbai", "Pune",
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "AC Sleeper",
                new FareAmount(BigDecimal.valueOf(500), Currency.getInstance("INR")), 10);
        client.searchResult = () -> List.of(trip);

        SearchTrips service = new SearchTripsService(new ActiveSessionResolver(sessionRepository, clock),
                new ProviderClientRegistry(List.of(client)));

        SearchTrips.Result result = service.search(new SearchTrips.Command(sessionId,
                new SearchCriteria("Mumbai", "Pune", LocalDate.of(2026, 8, 1))));

        assertThat(result.trips()).containsExactly(trip);
        assertThat(client.searchCallCount).isEqualTo(1);
    }
}
