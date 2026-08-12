package com.roadscanner.inventoryservice.adapter.in.rest.city;

import com.roadscanner.inventoryservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.inventoryservice.domain.exception.CityNotFoundException;
import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.port.in.LinkCityToCanonicalLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The administrative route that binds catalog geography to canonical locations. */
@WebMvcTest(CityCanonicalLocationController.class)
@Import(GlobalExceptionHandler.class)
class CityCanonicalLocationControllerTest {

    private static final UUID CITY_ID = UUID.fromString("11111111-1111-1111-1111-111111111106");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LinkCityToCanonicalLocation linkCityToCanonicalLocation;

    @Test
    void recordsTheLinkAndEchoesTheCityBack() throws Exception {
        UUID canonical = UUID.randomUUID();
        when(linkCityToCanonicalLocation.link(any())).thenReturn(new LinkCityToCanonicalLocation.Result(
                City.reconstitute(new CityId(CITY_ID), "Hyderabad", "Telangana", "India", canonical)));

        mockMvc.perform(put("/internal/api/v1/inventory/cities/{cityId}/canonical-location", CITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalLocationId\":\"" + canonical + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cityId").value(CITY_ID.toString()))
                .andExpect(jsonPath("$.name").value("Hyderabad"))
                .andExpect(jsonPath("$.canonicalLocationId").value(canonical.toString()));
    }

    @Test
    void returns404ForACityThatDoesNotExist() throws Exception {
        when(linkCityToCanonicalLocation.link(any()))
                .thenThrow(new CityNotFoundException(new CityId(CITY_ID)));

        mockMvc.perform(put("/internal/api/v1/inventory/cities/{cityId}/canonical-location", CITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalLocationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns409WhenTheCityIsAlreadyLinkedElsewhere() throws Exception {
        when(linkCityToCanonicalLocation.link(any()))
                .thenThrow(new IllegalStateException("City is already linked to canonical location X"));

        // Distinct from a 400: the request is well-formed, it conflicts with state that already
        // exists — and repointing would orphan every trip already synchronised under the old link.
        mockMvc.perform(put("/internal/api/v1/inventory/cities/{cityId}/canonical-location", CITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalLocationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsAMissingCanonicalLocationIdRatherThanLinkingToNothing() throws Exception {
        mockMvc.perform(put("/internal/api/v1/inventory/cities/{cityId}/canonical-location", CITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
