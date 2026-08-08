package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.searchservice.config.SecurityConfig;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.in.ResolveProviderLocations;
import com.roadscanner.searchservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The wire contract {@code inventory-service}'s catalog sync depends on.
 *
 * <p>Reachable without a token, like the rest of {@code /internal} — this is a service-to-service
 * route, and the administrative surface over the same table keeps its {@code ROLE_ADMIN} guard on
 * {@code /api/v1/provider-mappings}.
 */
@WebMvcTest(controllers = ProviderLocationResolutionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class, NoOpJwtDecoderConfig.class})
class ProviderLocationResolutionControllerTest {

    private static final UUID HYDERABAD = UUID.fromString("11111111-1111-1111-1111-111111111106");
    private static final UUID UNMAPPED = UUID.fromString("11111111-1111-1111-1111-111111111101");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResolveProviderLocations resolveProviderLocations;

    @Test
    void returnsCityIdsForResolvableLocationsAndNamesTheRest() throws Exception {
        when(resolveProviderLocations.resolve(any())).thenReturn(new ResolveProviderLocations.Result(
                Map.of(new LocationId(HYDERABAD), "3da253ae-02ca-430c-87e5-22842065a77d"),
                List.of(new LocationId(UNMAPPED))));

        mockMvc.perform(get("/internal/api/v1/provider-locations/FLIXBUS/resolve")
                        .param("locationIds", HYDERABAD.toString(), UNMAPPED.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("FLIXBUS"))
                .andExpect(jsonPath("$.cityIdsByLocation." + HYDERABAD)
                        .value("3da253ae-02ca-430c-87e5-22842065a77d"))
                // Named, not silently omitted: the caller has to distinguish "not served" from
                // "mapping missing", and both differ from an empty search result.
                .andExpect(jsonPath("$.unresolved[0]").value(UNMAPPED.toString()));
    }

    @Test
    void rejectsARequestWithNoLocationIds() throws Exception {
        mockMvc.perform(get("/internal/api/v1/provider-locations/FLIXBUS/resolve"))
                .andExpect(status().isBadRequest());
    }
}
