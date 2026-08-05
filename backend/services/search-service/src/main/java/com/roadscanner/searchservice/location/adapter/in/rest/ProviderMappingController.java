package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.exception.ProviderMappingNotFoundException;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.in.ManageProviderMappings;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderMappings;
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
 * Administrative management of the provider translation layer.
 *
 * <p><strong>Every route here requires {@code ROLE_ADMIN}, including the reads.</strong> That is
 * stricter than the rest of this service, whose location reads are deliberately public, and the
 * asymmetry is the point: these are the only responses in the platform that carry a provider's own
 * identifiers. Keeping provider vocabulary out of traveller-facing contracts is what the location
 * module exists to guarantee, so the gate is on the whole surface rather than on the writes alone.
 *
 * <p>The controller creates no canonical locations. An administrator maps places that already
 * exist in the catalogue, authored through {@code /api/v1/locations} or enriched from Google
 * Places; letting a provider's vocabulary mint RoadScanner places would invert the direction the
 * catalogue is authored in.
 *
 * <p>Provider-neutral throughout. Nothing here branches on which provider a mapping belongs to —
 * {@link ProviderCode} is an open string, so onboarding the next provider is a registration, its
 * credentials, and rows in this table.
 */
@RestController
@RequestMapping("/api/v1/provider-mappings")
@Validated
@Tag(name = "Provider Mappings", description = "The provider location translation layer (admin)")
class ProviderMappingController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_UNMAPPED_LIMIT = 50;

    private final SearchProviderMappings searchProviderMappings;
    private final ManageProviderMappings manageProviderMappings;
    private final GetLocation getLocation;

    ProviderMappingController(SearchProviderMappings searchProviderMappings,
                              ManageProviderMappings manageProviderMappings, GetLocation getLocation) {
        this.searchProviderMappings = searchProviderMappings;
        this.manageProviderMappings = manageProviderMappings;
        this.getLocation = getLocation;
    }

    @GetMapping
    @Operation(summary = "List and search mappings (admin)",
            description = "Filters combine with AND and are all optional. The search term spans the "
                    + "canonical location's display name and city as well as the provider's city id, "
                    + "station id and station name — an operator holds whichever of those the "
                    + "provider's own console showed them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of mappings, most recently updated first"),
            @ApiResponse(responseCode = "400", description = "Malformed paging or filter parameters"),
            @ApiResponse(responseCode = "403", description = "Not an administrator")
    })
    ProviderMappingPageResponse list(
            @Parameter(description = "Restrict to one provider", example = "FLIXBUS")
            @RequestParam(required = false) String provider,

            @Parameter(description = "Restrict to confirmed or unconfirmed mappings")
            @RequestParam(required = false) Boolean verified,

            @Parameter(description = "Free-text term, matched anywhere in the searchable fields")
            @RequestParam(required = false) String q,

            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(MAX_PAGE_SIZE) Integer size) {

        SearchProviderMappings.Result result = searchProviderMappings.search(new SearchProviderMappings.Query(
                toProviderCode(provider), verified, q,
                page != null ? page : 0,
                size != null ? size : DEFAULT_PAGE_SIZE));

        return ProviderMappingPageResponse.from(result);
    }

    /**
     * The onboarding worklist: canonical locations this provider cannot yet express.
     *
     * <p>Nested under mappings rather than under {@code /locations} because the question is about
     * the translation layer — "what does this provider still need?" is the complement of "what is
     * mapped?", and the public location surface must not gain a provider-shaped query parameter.
     */
    @GetMapping("/unmapped-locations")
    @Operation(summary = "List canonical locations with no mapping for a provider (admin)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unmapped active locations, alphabetical"),
            @ApiResponse(responseCode = "400", description = "Missing provider or out-of-range limit"),
            @ApiResponse(responseCode = "403", description = "Not an administrator")
    })
    UnmappedLocationsResponse unmappedLocations(
            @Parameter(description = "The provider whose mappings are missing", example = "FLIXBUS")
            @RequestParam @NotBlank(message = "provider is required") String provider,

            @Parameter(description = "Optional free-text term over display name and city")
            @RequestParam(required = false) String q,

            @RequestParam(required = false) @Min(1) @Max(200) Integer limit) {

        SearchProviderMappings.UnmappedResult result = searchProviderMappings.findUnmappedLocations(
                new SearchProviderMappings.UnmappedQuery(new ProviderCode(provider), q,
                        limit != null ? limit : DEFAULT_UNMAPPED_LIMIT));

        return new UnmappedLocationsResponse(
                result.locations().stream().map(LocationSummary::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a mapping (admin)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The mapping"),
            @ApiResponse(responseCode = "404", description = "No mapping with this id"),
            @ApiResponse(responseCode = "403", description = "Not an administrator")
    })
    ProviderMappingResponse get(@PathVariable UUID id) {
        ProviderLocationMappingId mappingId = new ProviderLocationMappingId(id);
        ProviderLocationMapping mapping = manageProviderMappings.getById(mappingId)
                .orElseThrow(() -> new ProviderMappingNotFoundException(mappingId));

        return ProviderMappingResponse.from(mapping, resolveLocation(mapping.locationId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a mapping (admin)",
            description = "The canonical location must already exist — this endpoint never creates one.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; Location header carries the new resource"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or neither a city nor a station id was supplied"),
            @ApiResponse(responseCode = "404", description = "No such canonical location"),
            @ApiResponse(responseCode = "409", description = "Would duplicate an existing mapping or reuse a provider id"),
            @ApiResponse(responseCode = "403", description = "Not an administrator")
    })
    ResponseEntity<ProviderMappingResponse> create(@Valid @RequestBody ProviderMappingRequest request) {
        if (request.locationId() == null) {
            throw new IllegalArgumentException("locationId is required when creating a mapping");
        }
        if (request.provider() == null || request.provider().isBlank()) {
            throw new IllegalArgumentException("provider is required when creating a mapping");
        }

        ProviderLocationMapping mapping = manageProviderMappings.create(new ManageProviderMappings.CreateCommand(
                new LocationId(request.locationId()),
                new ProviderCode(request.provider()),
                toPlaceRef(request),
                request.providerMetadata(),
                Boolean.TRUE.equals(request.verified())));

        ProviderMappingResponse body = ProviderMappingResponse.from(mapping, resolveLocation(mapping.locationId()));
        URI location = UriComponentsBuilder.fromPath("/api/v1/provider-mappings/{id}")
                .buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a mapping (admin)",
            description = "A full replace of the editable fields. The canonical location and the "
                    + "provider are immutable — supply them and they are ignored. Re-pointing a "
                    + "mapping is a delete plus a create.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated mapping"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "No mapping with this id"),
            @ApiResponse(responseCode = "409", description = "Would reuse a provider id already mapped elsewhere"),
            @ApiResponse(responseCode = "403", description = "Not an administrator")
    })
    ProviderMappingResponse update(@PathVariable UUID id, @Valid @RequestBody ProviderMappingRequest request) {
        ProviderLocationMapping mapping = manageProviderMappings.update(new ManageProviderMappings.UpdateCommand(
                new ProviderLocationMappingId(id),
                toPlaceRef(request),
                request.providerMetadata(),
                Boolean.TRUE.equals(request.verified())));

        return ProviderMappingResponse.from(mapping, resolveLocation(mapping.locationId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a mapping (admin)",
            description = "A hard delete, and idempotent — unlike a canonical location, a mapping "
                    + "is a translation rule with nothing referring to it, and a wrong translation "
                    + "needs to stop existing rather than linger as an inactive row.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted, or already absent"),
            @ApiResponse(responseCode = "403", description = "Not an administrator")
    })
    void delete(@PathVariable UUID id) {
        manageProviderMappings.delete(new ProviderLocationMappingId(id));
    }

    /**
     * Resolves the canonical location for a single mapping response.
     *
     * <p>Goes through {@link GetLocation} rather than a repository so the controller keeps to the
     * ports it is allowed to see. The list path does not use this — it resolves locations in a
     * batch inside {@code SearchProviderMappingsService}, so a page of twenty rows is not twenty
     * lookups.
     */
    private Location resolveLocation(LocationId locationId) {
        try {
            return getLocation.get(new GetLocation.GetLocationCommand(locationId)).location();
        } catch (LocationNotFoundException e) {
            // The foreign key makes this unreachable. Rethrowing rather than substituting a
            // placeholder keeps a genuine integrity failure loud instead of rendering a row an
            // operator cannot act on.
            throw e;
        }
    }

    private static ProviderCode toProviderCode(String raw) {
        return raw == null || raw.isBlank() ? null : new ProviderCode(raw);
    }

    /** The "at least a city id or a station id" rule lives in {@link ProviderPlaceRef}, which
     * throws {@code IllegalArgumentException} — mapped to 400 by the global handler. */
    private static ProviderPlaceRef toPlaceRef(ProviderMappingRequest request) {
        return new ProviderPlaceRef(request.providerCityId(), request.providerStationId(),
                request.providerStationName());
    }

    record UnmappedLocationsResponse(java.util.List<LocationSummary> locations) {
    }
}
