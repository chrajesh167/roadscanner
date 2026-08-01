package com.roadscanner.providerintegrationservice.application.usecase.search;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchProviderTrips;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.StubProviderClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The generic, session-less provider search: it works for any provider, refuses one that is
 * switched off, and never requires a login the provider did not ask for.
 */
class SearchProviderTripsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final SearchCriteria CRITERIA =
            new SearchCriteria("58291", "41100", LocalDate.of(2026, 8, 1));

    private InMemoryProviderConfigurationRepository providers;
    private StubProviderClient client;

    @BeforeEach
    void setUp() {
        providers = new InMemoryProviderConfigurationRepository();
        client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEARCH));
    }

    private static Provider provider(ProviderType type, boolean enabled, Set<ProviderCapability> capabilities) {
        return Provider.reconstitute(ProviderId.generate(), type, ProviderCategory.BUS, "Test", enabled,
                capabilities, null, 5_000, 1, NOW, NOW);
    }

    private SearchProviderTrips useCase() {
        return new SearchProviderTripsService(providers, new ProviderClientRegistry(List.of(client)));
    }

    private static ProviderTrip trip() {
        return new ProviderTrip("TRIP-1", ProviderType.MOCK, "Mock Travels", "Mumbai", "Pune",
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "AC Sleeper",
                new FareAmount(BigDecimal.valueOf(500), Currency.getInstance("INR")), 10,
                "point-a", "point-b");
    }

    @Test
    void searchesWithoutRequiringAnAuthenticatedSession() {
        providers.add(provider(ProviderType.MOCK, true, Set.of(ProviderCapability.SEARCH)));
        client.searchResult = () -> List.of(trip());

        var result = useCase().search(new SearchProviderTrips.Command(ProviderType.MOCK, CRITERIA));

        // No session is created, resolved or required anywhere in this flow — that is the point.
        assertThat(result.trips()).hasSize(1);
        assertThat(result.trips().getFirst().providerTripId()).isEqualTo("TRIP-1");
    }

    @Test
    void carriesTheBoardingIdentifiersLaterBookingWillNeed() {
        providers.add(provider(ProviderType.MOCK, true, Set.of(ProviderCapability.SEARCH)));
        client.searchResult = () -> List.of(trip());

        ProviderTrip found = useCase().search(
                new SearchProviderTrips.Command(ProviderType.MOCK, CRITERIA)).trips().getFirst();

        // Search is the only moment a provider hands these over; dropping them here would make
        // booking impossible later without a second round trip.
        assertThat(found.boardingPointIdIfPresent()).contains("point-a");
        assertThat(found.alightingPointIdIfPresent()).contains("point-b");
    }

    @Test
    void returnsAnEmptyListWhenTheProviderHasNoMatchingTrips() {
        providers.add(provider(ProviderType.MOCK, true, Set.of(ProviderCapability.SEARCH)));
        client.searchResult = List::of;

        assertThat(useCase().search(new SearchProviderTrips.Command(ProviderType.MOCK, CRITERIA)).trips()).isEmpty();
    }

    @Test
    void refusesAProviderThatIsNotRegistered() {
        assertThatThrownBy(() -> useCase().search(new SearchProviderTrips.Command(ProviderType.MOCK, CRITERIA)))
                .isInstanceOf(ProviderNotSupportedException.class);
    }

    @Test
    void refusesADisabledProviderBeforeCallingIt() {
        providers.add(provider(ProviderType.MOCK, false, Set.of(ProviderCapability.SEARCH)));

        // An administrator who disabled a provider expects traffic to stop immediately, not to
        // keep flowing until the adapter happens to fail.
        assertThatThrownBy(() -> useCase().search(new SearchProviderTrips.Command(ProviderType.MOCK, CRITERIA)))
                .isInstanceOf(ProviderNotSupportedException.class);
    }

    @Test
    void refusesAProviderThatDoesNotSupportSearch() {
        providers.add(provider(ProviderType.MOCK, true, Set.of(ProviderCapability.SEAT_MAP)));
        client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEAT_MAP));

        assertThatThrownBy(() -> useCase().search(new SearchProviderTrips.Command(ProviderType.MOCK, CRITERIA)))
                .isInstanceOf(ProviderNotSupportedException.class);
    }

    @Test
    void theCommandRejectsAMissingProviderOrCriteria() {
        assertThatThrownBy(() -> new SearchProviderTrips.Command(null, CRITERIA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchProviderTrips.Command(ProviderType.MOCK, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aNonBusProviderCanBuildATripWithoutPlaceholderValues() {
        // The test that matters for transport neutrality: a rail or airline adapter populates the
        // same record without inventing "Bus", "Coach" or "N/A" anywhere.
        ProviderTrip railTrip = new ProviderTrip("IC-401", ProviderType.MOCK, "National Rail",
                "London", "Edinburgh", NOW.plusSeconds(3600), NOW.plusSeconds(20000),
                "First Class", new FareAmount(BigDecimal.valueOf(120), Currency.getInstance("INR")), 40,
                "KGX", "EDB");

        assertThat(railTrip.serviceClassIfPresent()).contains("First Class");
        assertThat(railTrip.boardingPointIdIfPresent()).contains("KGX");
    }

    @Test
    void aProviderThatDoesNotClassifyItsServiceIsNotForcedToInventOne() {
        ProviderTrip unclassified = new ProviderTrip("FERRY-9", ProviderType.MOCK, "Ferry Co",
                "Dover", "Calais", NOW.plusSeconds(3600), NOW.plusSeconds(9000),
                null, new FareAmount(BigDecimal.valueOf(30), Currency.getInstance("INR")), 200, null, null);

        // Optional, not mandatory — making it required would recreate the original busType defect
        // under a new name.
        assertThat(unclassified.serviceClassIfPresent()).isEmpty();
        assertThat(unclassified.boardingPointIdIfPresent()).isEmpty();
    }
}
