package com.roadscanner.inventoryservice.application.usecase.availability;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.GetTripAvailability;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryProviderMappingRepository;
import com.roadscanner.inventoryservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the "degrade, not fail" contract this facade exists to guarantee — see
 * docs/services/inventory-service/boundaries.md. */
class GetTripAvailabilityServiceTest {

    private final InMemoryProviderMappingRepository providerMappingRepository = new InMemoryProviderMappingRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final GetTripAvailabilityService service =
            new GetTripAvailabilityService(providerMappingRepository, providerIntegrationClient);

    @Test
    void returnsKnownCountWhenMappingExistsAndProviderAnswers() {
        TripId tripId = TripId.generate();
        providerMappingRepository.save(ProviderMapping.create(tripId, new ProviderType("MOCK"), "MOCK-TRIP-1", Instant.now()));
        providerIntegrationClient.availableSeatCountResult = OptionalInt.of(12);

        GetTripAvailability.Result result = service.get(new GetTripAvailability.Command(tripId));

        assertThat(result.availableSeats()).hasValue(12);
    }

    @Test
    void degradesToUnknownWhenNoMappingExists() {
        GetTripAvailability.Result result = service.get(new GetTripAvailability.Command(TripId.generate()));

        assertThat(result.availableSeats()).isEmpty();
    }

    @Test
    void degradesToUnknownWhenProviderCannotAnswer() {
        TripId tripId = TripId.generate();
        providerMappingRepository.save(ProviderMapping.create(tripId, new ProviderType("MOCK"), "MOCK-TRIP-1", Instant.now()));
        providerIntegrationClient.availableSeatCountResult = OptionalInt.empty();

        GetTripAvailability.Result result = service.get(new GetTripAvailability.Command(tripId));

        assertThat(result.availableSeats()).isEmpty();
    }
}
