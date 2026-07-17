package com.roadscanner.searchservice.domain.model;

/**
 * How to order a {@code SearchResultPage} — FR-2.3's "price, departure time, duration,
 * operator rating." Resolution of "no option specified" to a concrete default is
 * {@link com.roadscanner.searchservice.domain.service.SearchRankingPolicy}'s job, not this
 * enum's — see that class's Javadoc.
 */
public enum SortOption {
    PRICE_ASC,
    PRICE_DESC,
    DEPARTURE_TIME_ASC,
    DURATION_ASC,
    RATING_DESC
}
