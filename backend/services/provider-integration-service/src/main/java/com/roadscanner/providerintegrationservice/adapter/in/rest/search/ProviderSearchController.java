package com.roadscanner.providerintegrationservice.adapter.in.rest.search;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchTrips;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Internal-only — searches the provider identified by an already-open session. */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/sessions/{sessionId}/trips")
@Validated
@Tag(name = "Provider Search", description = "Search a provider for trips")
class ProviderSearchController {

    private final SearchTrips searchTrips;

    ProviderSearchController(SearchTrips searchTrips) {
        this.searchTrips = searchTrips;
    }

    @GetMapping
    @Operation(summary = "Search trips", description = "Searches the provider bound to sessionId for trips matching origin/destination/date.")
    SearchTripsResponse search(@PathVariable String providerType, @PathVariable UUID sessionId,
                                @RequestParam @NotBlank String origin, @RequestParam @NotBlank String destination,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                @Parameter(example = "2026-08-01") LocalDate date) {
        SearchTrips.Result result = searchTrips.search(new SearchTrips.Command(new ProviderSessionId(sessionId),
                new SearchCriteria(origin, destination, date)));
        return SearchTripsResponse.from(result);
    }
}
