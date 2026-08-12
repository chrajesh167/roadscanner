package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.exception.ProviderTripNotMappedException;
import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.ResolveProviderTrip;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryProviderMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The translation that lets a live provider trip enter the one booking flow.
 *
 * <p>Search identifies a provider trip by {@code (providerCode, providerTripId)}; everything
 * downstream of seat selection is keyed by a catalog trip UUID. This resolves between them, and the
 * property that matters most is the one pinned by the last test: a provider trip nobody has
 * reconciled produces a refusal, never an invented id.
 */
class ResolveProviderTripServiceTest {

    private static final ProviderType MOCK = new ProviderType("MOCK");
    private static final String PROVIDER_TRIP_ID = "MOCK-HYDERABAD-BENGALURU-2026-08-20-AC-SLEEPER";

    private InMemoryProviderMappingRepository mappings;
    private ResolveProviderTrip resolve;

    @BeforeEach
    void setUp() {
        mappings = new InMemoryProviderMappingRepository();
        resolve = new ResolveProviderTripService(mappings);
    }

    @Test
    void resolvesAProviderTripToTheCatalogTripThatRepresentsIt() {
        TripId tripId = new TripId(UUID.randomUUID());
        mappings.save(ProviderMapping.create(tripId, MOCK, PROVIDER_TRIP_ID, Instant.parse("2026-08-11T00:00:00Z")));

        ResolveProviderTrip.Result result =
                resolve.resolve(new ResolveProviderTrip.Command(MOCK, PROVIDER_TRIP_ID));

        assertThat(result.mapping().tripId()).isEqualTo(tripId);
        assertThat(result.mapping().providerTripId()).isEqualTo(PROVIDER_TRIP_ID);
    }

    @Test
    void theSameProviderTripIdUnderADifferentProviderIsADifferentTrip() {
        TripId mockTrip = new TripId(UUID.randomUUID());
        mappings.save(ProviderMapping.create(mockTrip, MOCK, PROVIDER_TRIP_ID, Instant.parse("2026-08-11T00:00:00Z")));

        // Provider trip ids are only unique within a provider — the mapping table's unique
        // constraint is on the pair, so resolution must key on the pair too.
        assertThatThrownBy(() -> resolve.resolve(
                new ResolveProviderTrip.Command(new ProviderType("FLIXBUS"), PROVIDER_TRIP_ID)))
                .isInstanceOf(ProviderTripNotMappedException.class);
    }

    @Test
    void anUnreconciledProviderTripIsRefusedRatherThanGivenAnInventedCatalogId() {
        assertThatThrownBy(() -> resolve.resolve(new ResolveProviderTrip.Command(MOCK, PROVIDER_TRIP_ID)))
                .isInstanceOf(ProviderTripNotMappedException.class)
                .hasMessageContaining(PROVIDER_TRIP_ID);
    }

    @Test
    void aBlankProviderTripIdIsRejectedBeforeItReachesTheRepository() {
        assertThatThrownBy(() -> new ResolveProviderTrip.Command(MOCK, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
