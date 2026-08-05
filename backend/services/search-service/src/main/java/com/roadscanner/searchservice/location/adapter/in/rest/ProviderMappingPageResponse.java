package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.port.in.SearchProviderMappings;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of mappings plus the totals a table needs to render "x of y" and size its pager.
 *
 * <p>Totals come from the same query that produced the rows, so the count and the content cannot
 * disagree — a separate count endpoint would leave the client stitching together two answers taken
 * at different moments.
 */
@Schema(name = "ProviderMappingPage", description = "A page of provider location mappings")
public record ProviderMappingPageResponse(

        List<ProviderMappingResponse> mappings,
        long totalElements,
        int totalPages,
        int page,
        int size
) {

    public static ProviderMappingPageResponse from(SearchProviderMappings.Result result) {
        return new ProviderMappingPageResponse(
                result.rows().stream()
                        .map(row -> ProviderMappingResponse.from(row.mapping(), row.location()))
                        .toList(),
                result.totalElements(),
                result.totalPages(),
                result.page(),
                result.size());
    }
}
