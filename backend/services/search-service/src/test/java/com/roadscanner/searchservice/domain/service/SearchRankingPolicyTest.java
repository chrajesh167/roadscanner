package com.roadscanner.searchservice.domain.service;

import com.roadscanner.searchservice.domain.model.SortOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRankingPolicyTest {

    private final SearchRankingPolicy policy = new SearchRankingPolicy();

    @Test
    void resolvesNullToDepartureTimeAscending() {
        assertThat(policy.resolve(null)).isEqualTo(SortOption.DEPARTURE_TIME_ASC);
        assertThat(policy.defaultSort()).isEqualTo(SortOption.DEPARTURE_TIME_ASC);
    }

    @Test
    void passesThroughAnExplicitRequest() {
        assertThat(policy.resolve(SortOption.RATING_DESC)).isEqualTo(SortOption.RATING_DESC);
    }
}
