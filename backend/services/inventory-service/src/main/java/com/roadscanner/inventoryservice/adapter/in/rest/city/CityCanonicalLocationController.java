package com.roadscanner.inventoryservice.adapter.in.rest.city;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.port.in.LinkCityToCanonicalLocation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administrative: records which canonical location a catalog city is.
 *
 * <p>Separate from {@link CityController} because it is a different audience — that one backs
 * traveller-facing autocomplete, this one is operated by whoever administers catalog geography.
 *
 * <p>{@code PUT} rather than {@code POST}: naming the same city the same location twice is the same
 * outcome as doing it once, and an operator re-running a setup script should not get a conflict for
 * work already done. Naming it a <em>different</em> location is refused by the domain — see
 * {@code City#linkToCanonicalLocation}.
 *
 * <p>Under {@code /internal/} rather than the public {@code /api/v1/inventory/} prefix: this writes
 * administratively-owned reference data and is not part of any traveller flow. Like every other
 * {@code /internal/**} surface on this platform it has no authentication of its own and expects
 * {@code api-gateway} never to route it publicly — the same disclosed gap recorded in
 * provider-integration-service's and search-service's READMEs, not a new one.
 */
@RestController
@RequestMapping("/internal/api/v1/inventory/cities/{cityId}/canonical-location")
@Tag(name = "Catalog Administration", description = "Bind catalog geography to canonical locations")
class CityCanonicalLocationController {

    private final LinkCityToCanonicalLocation linkCityToCanonicalLocation;

    CityCanonicalLocationController(LinkCityToCanonicalLocation linkCityToCanonicalLocation) {
        this.linkCityToCanonicalLocation = linkCityToCanonicalLocation;
    }

    @PutMapping
    @Operation(summary = "Link a city to its canonical location",
            description = "Records which search-service location this catalog city is, so catalog sync can "
                    + "translate it into each provider's own city id. Until this is set, every route through "
                    + "the city is skipped.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link recorded"),
            @ApiResponse(responseCode = "400", description = "Malformed request"),
            @ApiResponse(responseCode = "404", description = "No such city"),
            @ApiResponse(responseCode = "409", description = "Already linked to a different canonical location")
    })
    CanonicalLocationLinkResponse link(@PathVariable UUID cityId,
                                        @Valid @RequestBody LinkCanonicalLocationRequest request) {
        LinkCityToCanonicalLocation.Result result = linkCityToCanonicalLocation.link(
                new LinkCityToCanonicalLocation.Command(new CityId(cityId), request.canonicalLocationId()));
        return CanonicalLocationLinkResponse.from(result.city());
    }
}
