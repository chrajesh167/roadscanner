package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.TripSearchResult;

import java.util.List;

/** A ranked, paged page of {@link TripResponse}s — the response shape of "Search Trips" (FR-2.1–FR-2.3). */
public record SearchResultResponse(
        List<TripResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static SearchResultResponse from(ResultPage<TripSearchResult> page) {
        return new SearchResultResponse(
                page.content().stream().map(TripResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
