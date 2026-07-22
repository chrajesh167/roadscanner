package com.roadscanner.inventoryservice.adapter.in.rest.city;

import com.roadscanner.inventoryservice.domain.port.in.BrowseCities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catalog geography browsing — search-form autocomplete (FR-2.1's origin/destination input). */
@RestController
@RequestMapping("/api/v1/inventory/cities")
@Validated
@Tag(name = "Cities", description = "Browse catalog cities")
class CityController {

    private final BrowseCities browseCities;

    CityController(BrowseCities browseCities) {
        this.browseCities = browseCities;
    }

    @GetMapping
    @Operation(summary = "Browse cities", description = "Prefix search over catalog cities, for autocomplete.")
    CitiesResponse browse(@RequestParam(required = false, defaultValue = "") String q,
                           @RequestParam(required = false) @Min(1) @Max(50) Integer limit) {
        BrowseCities.Result result = browseCities.browse(new BrowseCities.Command(q, limit != null ? limit : 10));
        return CitiesResponse.from(result);
    }
}
