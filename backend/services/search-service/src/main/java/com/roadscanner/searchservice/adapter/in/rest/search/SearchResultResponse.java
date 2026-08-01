package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.TripSearchResult;

import java.util.List;

/** A ranked, paged page of {@link TripResponse}s — the response shape of "Search Trips" (FR-2.1–FR-2.3). */
/**
 * One search answer: indexed first-party trips, plus live provider trips when the caller supplied
 * canonical location ids.
 *
 * <p>Provider results are a separate field rather than merged into {@code content} because they are
 * a different kind of thing: {@code content} is a paged projection of the index, while provider
 * trips are fetched live and are not paged, ranked or persisted. Flattening them together would
 * make paging meaningless — page 2 of a list half of which was fetched live is not a coherent
 * concept — and would hide which results are re-validated at hold time.
 *
 * <p>{@code providerSearchComplete} is false when at least one provider failed, so a caller can
 * tell a partial answer from a complete one instead of presenting it as the whole market.
 */
public record SearchResultResponse(
        List<TripResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ProviderTripResponse> providerTrips,
        boolean providerSearchComplete
) {

    /** Index-only answer, for callers that did not supply canonical location ids. */
    public static SearchResultResponse from(ResultPage<TripSearchResult> page) {
        return from(page, com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips.Result
                .empty());
    }

    public static SearchResultResponse from(ResultPage<TripSearchResult> page,
            com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips.Result providerResult) {
        return new SearchResultResponse(
                page.content().stream().map(TripResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                providerResult.trips().stream().map(ProviderTripResponse::from).toList(),
                providerResult.complete());
    }
}
