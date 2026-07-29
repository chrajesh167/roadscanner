package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.in.CreateLocation;
import com.roadscanner.searchservice.location.domain.port.in.DisableLocation;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.in.SearchLocations;
import com.roadscanner.searchservice.location.domain.port.in.UpdateLocation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * The canonical location catalogue's HTTP surface.
 *
 * <p>Every response here speaks exclusively in RoadScanner {@code LocationId}s. No endpoint
 * accepts or returns a Google Place ID as an identifier, and none exposes a provider's own ids —
 * provider translation lives behind {@code GetProviderMapping}, which is intentionally an
 * in-process port with no route.
 *
 * <p>Admin authorisation is not enforced here: search-service currently runs no
 * {@code SecurityFilterChain} (it is a public read service), so adding role checks to these
 * routes would be theatre. The three admin operations are marked as such in their OpenAPI
 * descriptions and must be gated at {@code api-gateway}, or by adding security to this service —
 * see the Sprint 1 handover notes.
 */
@RestController
@RequestMapping("/api/v1/locations")
@Validated
@Tag(name = "Locations", description = "The canonical RoadScanner location catalogue")
class LocationController {

    private static final int DEFAULT_LIMIT = 10;

    private final SearchLocations searchLocations;
    private final GetLocation getLocation;
    private final CreateLocation createLocation;
    private final UpdateLocation updateLocation;
    private final DisableLocation disableLocation;

    LocationController(SearchLocations searchLocations, GetLocation getLocation, CreateLocation createLocation,
                       UpdateLocation updateLocation, DisableLocation disableLocation) {
        this.searchLocations = searchLocations;
        this.getLocation = getLocation;
        this.createLocation = createLocation;
        this.updateLocation = updateLocation;
        this.disableLocation = disableLocation;
    }

    @GetMapping
    @Operation(summary = "Autocomplete locations",
            description = "Prefix match on display name or city over active catalogue entries. "
                    + "Soft-deleted locations are never suggested.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suggestions, best match first (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Blank query or out-of-range limit")
    })
    AutocompleteResponse autocomplete(
            @Parameter(description = "Search fragment", example = "hyd")
            @RequestParam("q") @NotBlank(message = "q must not be blank") String query,

            @Parameter(description = "Maximum suggestions to return (1-25)", example = "10")
            @RequestParam(required = false) @Min(1) @Max(25) Integer limit) {

        int resolvedLimit = limit != null ? limit : DEFAULT_LIMIT;
        SearchLocations.SearchLocationsResult result =
                searchLocations.search(new SearchLocations.SearchLocationsCommand(query, resolvedLimit));
        return AutocompleteResponse.from(result.locations());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a location",
            description = "Resolves a location by its canonical RoadScanner id. Soft-deleted "
                    + "locations are returned too, so historical references stay resolvable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The location"),
            @ApiResponse(responseCode = "404", description = "No location with this id")
    })
    LocationResponse get(@PathVariable UUID id) {
        GetLocation.GetLocationResult result = getLocation.get(new GetLocation.GetLocationCommand(new LocationId(id)));
        return LocationResponse.from(result.location());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a location (admin)",
            description = "Adds a catalogue entry. googlePlaceId is optional but must be unique "
                    + "across the catalogue when supplied.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; Location header carries the new resource"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "googlePlaceId already belongs to another location")
    })
    ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        CreateLocation.CreateLocationResult result = createLocation.create(new CreateLocation.CreateLocationCommand(
                request.displayName(),
                toAddress(request),
                toCoordinates(request),
                GooglePlaceId.ofNullable(request.googlePlaceId()),
                request.timezone()));

        LocationResponse body = LocationResponse.from(result.location());
        URI location = UriComponentsBuilder.fromPath("/api/v1/locations/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a location (admin)",
            description = "Full replace, not a patch: an omitted optional field clears it. "
                    + "The active flag is not editable here — use DELETE to withdraw a location.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated location"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "No location with this id"),
            @ApiResponse(responseCode = "409", description = "googlePlaceId already belongs to another location")
    })
    LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        UpdateLocation.UpdateLocationResult result = updateLocation.update(new UpdateLocation.UpdateLocationCommand(
                new LocationId(id),
                request.displayName(),
                toAddress(request),
                toCoordinates(request),
                GooglePlaceId.ofNullable(request.googlePlaceId()),
                request.timezone()));

        return LocationResponse.from(result.location());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw a location (admin)",
            description = "Soft delete only — the row is retained and stays resolvable by id, "
                    + "because historical trips and provider mappings may reference it. "
                    + "Idempotent: withdrawing an already-withdrawn location succeeds.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Withdrawn (or already withdrawn)"),
            @ApiResponse(responseCode = "404", description = "No location with this id")
    })
    void disable(@PathVariable UUID id) {
        disableLocation.disable(new DisableLocation.DisableLocationCommand(new LocationId(id)));
    }

    private static LocationAddress toAddress(LocationRequest request) {
        return new LocationAddress(request.city(), request.state(), request.country());
    }

    /** Rejects a half-supplied coordinate at the boundary, with the value object as the authority. */
    private static GeoCoordinates toCoordinates(LocationRequest request) {
        return GeoCoordinates.ofNullable(request.latitude(), request.longitude());
    }
}
