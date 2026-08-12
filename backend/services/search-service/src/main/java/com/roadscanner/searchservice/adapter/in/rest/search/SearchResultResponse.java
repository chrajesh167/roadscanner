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
 *
 * <p>A departure can legitimately appear in both lists: catalog sync imports provider trips, so the
 * same bus is both an indexed trip and a live provider result. This response reports both and links
 * them through {@link ProviderTripResponse#catalogTripId()} rather than dropping either. Suppressing
 * the indexed twin here would silently disagree with {@code totalElements} and {@code totalPages},
 * which are counts over the index, and would leave a page holding fewer rows than its own
 * {@code size} claims. Choosing which of two representations of one bus to show is a presentation
 * decision, and it is made where the identity link is now available to make it.
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
                .empty(), java.util.Map.of());
    }

    /**
     * @param catalogTripIds catalog trip id per provider trip id, for those provider trips catalog
     *                       sync has already imported. Absent entries are trips with no catalog
     *                       identity — see {@link ProviderTripResponse}.
     */
    public static SearchResultResponse from(ResultPage<TripSearchResult> page,
            com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips.Result providerResult,
            java.util.Map<String, java.util.UUID> catalogTripIds) {
        return new SearchResultResponse(
                page.content().stream().map(TripResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                providerResult.trips().stream()
                        .map(trip -> ProviderTripResponse.from(trip, catalogTripIds.get(trip.providerTripId())))
                        .toList(),
                providerResult.complete());
    }
}
