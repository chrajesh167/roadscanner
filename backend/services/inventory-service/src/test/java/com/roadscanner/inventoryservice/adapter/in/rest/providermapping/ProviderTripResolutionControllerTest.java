package com.roadscanner.inventoryservice.adapter.in.rest.providermapping;

import com.roadscanner.inventoryservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.inventoryservice.domain.exception.ProviderTripNotMappedException;
import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.ResolveProviderTrip;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The route customer-web depends on to turn a provider trip into something bookable.
 *
 * <p>Its path shape matters as much as its body: the provider trip id is a provider-authored
 * string, not a UUID, and the tests below pin that it survives the round trip intact — including
 * the hyphens and the date that make up MOCK's real id format.
 */
@WebMvcTest(ProviderTripResolutionController.class)
@Import(GlobalExceptionHandler.class)
class ProviderTripResolutionControllerTest {

    private static final String PROVIDER_TRIP_ID = "MOCK-HYDERABAD-BENGALURU-2026-08-20-AC-SLEEPER";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResolveProviderTrip resolveProviderTrip;

    @Test
    void returnsTheCatalogTripIdForAMappedProviderTrip() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(resolveProviderTrip.resolve(any())).thenReturn(new ResolveProviderTrip.Result(
                ProviderMapping.create(new TripId(tripId), new ProviderType("MOCK"), PROVIDER_TRIP_ID,
                        Instant.parse("2026-08-11T09:00:00Z"))));

        mockMvc.perform(get("/internal/api/v1/inventory/provider-trips/{provider}/{providerTripId}",
                        "MOCK", PROVIDER_TRIP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.providerType").value("MOCK"))
                .andExpect(jsonPath("$.providerTripId").value(PROVIDER_TRIP_ID));
    }

    @Test
    void passesTheProviderTripIdThroughVerbatimRatherThanNormalisingIt() throws Exception {
        when(resolveProviderTrip.resolve(any())).thenReturn(new ResolveProviderTrip.Result(
                ProviderMapping.create(new TripId(UUID.randomUUID()), new ProviderType("MOCK"), PROVIDER_TRIP_ID,
                        Instant.parse("2026-08-11T09:00:00Z"))));

        mockMvc.perform(get("/internal/api/v1/inventory/provider-trips/{provider}/{providerTripId}",
                "MOCK", PROVIDER_TRIP_ID));

        // The id is the provider's, and the mapping table is keyed by it exactly. A path variable
        // that arrived truncated at a hyphen or re-cased would silently resolve to nothing.
        ArgumentCaptor<ResolveProviderTrip.Command> command =
                ArgumentCaptor.forClass(ResolveProviderTrip.Command.class);
        verify(resolveProviderTrip).resolve(command.capture());
        assertThat(command.getValue().providerTripId()).isEqualTo(PROVIDER_TRIP_ID);
        assertThat(command.getValue().providerType().code()).isEqualTo("MOCK");
    }

    @Test
    void returns404WhenCatalogSyncHasNotReconciledThatProviderTrip() throws Exception {
        when(resolveProviderTrip.resolve(any())).thenThrow(
                new ProviderTripNotMappedException(new ProviderType("MOCK"), PROVIDER_TRIP_ID));

        // Not a 500 and not an empty 200: the provider trip is real, it simply has no catalog
        // identity yet, and the caller needs to be able to tell that from a broken lookup.
        mockMvc.perform(get("/internal/api/v1/inventory/provider-trips/{provider}/{providerTripId}",
                        "MOCK", PROVIDER_TRIP_ID))
                .andExpect(status().isNotFound());
    }
}
