package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingSnapshotTest {

    @Test
    void noneStartsAtZero() {
        assertThat(RatingSnapshot.none().average()).isZero();
        assertThat(RatingSnapshot.none().reviewCount()).isZero();
    }

    @Test
    void rejectsOutOfRangeAverage() {
        assertThatThrownBy(() -> new RatingSnapshot(-0.1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RatingSnapshot(5.1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeReviewCount() {
        assertThatThrownBy(() -> new RatingSnapshot(4.0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
