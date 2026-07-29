package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.GetProviderMapping;
import com.roadscanner.searchservice.location.testsupport.InMemoryLocationRepository;
import com.roadscanner.searchservice.location.testsupport.InMemoryProviderLocationMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProviderMappingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");
    private static final ProviderCode REDBUS = new ProviderCode("REDBUS");

    private InMemoryLocationRepository locations;
    private InMemoryProviderLocationMappingRepository mappings;
    private GetProviderMapping useCase;
    private Location hyderabad;

    @BeforeEach
    void setUp() {
        locations = new InMemoryLocationRepository();
        mappings = new InMemoryProviderLocationMappingRepository();
        useCase = new GetProviderMappingService(locations, mappings);

        hyderabad = Location.create(LocationId.generate(), "Hyderabad",
                new LocationAddress("Hyderabad", "Telangana", "India"), null, null, null, NOW);
        locations.seed(hyderabad);
    }

    private ProviderLocationMapping mapping(ProviderCode provider, String cityId, String stationId, String name) {
        return ProviderLocationMapping.create(ProviderLocationMappingId.generate(), provider, hyderabad.id(),
                new ProviderPlaceRef(cityId, stationId, name), null, NOW);
    }

    @Test
    void resolvesOneProvidersViewOfALocation() {
        mappings.seed(mapping(FLIXBUS, "58291", "station-1", "MGBS"));

        var result = useCase.get(new GetProviderMapping.GetProviderMappingCommand(hyderabad.id(), FLIXBUS));

        assertThat(result.mapping()).isPresent();
        assertThat(result.mapping().orElseThrow().placeRef().cityId()).isEqualTo("58291");
    }

    @Test
    void returnsEmptyWhenTheProviderDoesNotServeThePlace() {
        mappings.seed(mapping(FLIXBUS, "58291", "station-1", "MGBS"));

        var result = useCase.get(new GetProviderMapping.GetProviderMappingCommand(hyderabad.id(), REDBUS));

        // Absence is a normal answer, not an error — the caller decides whether it is fatal.
        assertThat(result.mapping()).isEmpty();
    }

    @Test
    void distinguishesAnUnknownLocationFromAnUnmappedOne() {
        LocationId unknown = LocationId.generate();

        assertThatThrownBy(() -> useCase.get(new GetProviderMapping.GetProviderMappingCommand(unknown, FLIXBUS)))
                .isInstanceOf(LocationNotFoundException.class);
    }

    @Test
    void returnsEveryProvidersMappingForOneLocation() {
        mappings.seed(
                mapping(FLIXBUS, "58291", "station-1", "MGBS"),
                mapping(REDBUS, "HYD", "rb-77", "Imliban"));

        var result = useCase.getAll(hyderabad.id());

        assertThat(result.mappings()).hasSize(2)
                .extracting(m -> m.provider().value())
                .containsExactlyInAnyOrder("FLIXBUS", "REDBUS");
    }

    @Test
    void getAllAlsoRejectsAnUnknownLocation() {
        LocationId unknown = LocationId.generate();

        assertThatThrownBy(() -> useCase.getAll(unknown)).isInstanceOf(LocationNotFoundException.class);
    }
}
