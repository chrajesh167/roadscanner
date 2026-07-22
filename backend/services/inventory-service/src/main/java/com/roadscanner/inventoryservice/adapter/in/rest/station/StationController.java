package com.roadscanner.inventoryservice.adapter.in.rest.station;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.port.in.BrowseStations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/stations")
@Validated
@Tag(name = "Stations", description = "Browse catalog stations")
class StationController {

    private final BrowseStations browseStations;

    StationController(BrowseStations browseStations) {
        this.browseStations = browseStations;
    }

    @GetMapping
    @Operation(summary = "Browse stations", description = "Prefix search over catalog stations, optionally scoped to a city.")
    StationsResponse browse(@RequestParam(required = false, defaultValue = "") String q,
                             @RequestParam(required = false) UUID cityId,
                             @RequestParam(required = false) @Min(1) @Max(50) Integer limit) {
        BrowseStations.Result result = browseStations.browse(new BrowseStations.Command(q,
                cityId != null ? new CityId(cityId) : null, limit != null ? limit : 10));
        return StationsResponse.from(result);
    }
}
