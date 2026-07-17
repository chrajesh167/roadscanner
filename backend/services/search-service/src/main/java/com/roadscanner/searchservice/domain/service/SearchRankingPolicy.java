package com.roadscanner.searchservice.domain.service;

import com.roadscanner.searchservice.domain.model.SortOption;

import java.util.Objects;

/**
 * Resolves "no sort specified" to a concrete default — earliest departure first, the most
 * intuitive ordering for a traveler who hasn't expressed a price/duration/rating preference.
 * Centralized here, rather than left to whichever layer happens to build the query first, for
 * the same reason {@code auth-service}'s {@code PasswordComplexityPolicy} centralizes its rule:
 * a decision like this should drift by one deliberate change, not by two call sites quietly
 * diverging (docs/services/auth-service/validation-strategy.md's rationale, applied here).
 */
public final class SearchRankingPolicy {

    private static final SortOption DEFAULT_SORT = SortOption.DEPARTURE_TIME_ASC;

    public SortOption resolve(SortOption requested) {
        return requested != null ? requested : DEFAULT_SORT;
    }

    public SortOption defaultSort() {
        return DEFAULT_SORT;
    }
}
