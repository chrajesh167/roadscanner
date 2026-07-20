package com.roadscanner.providerintegrationservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCriteriaTest {

    @Test
    void rejectsSameOriginAndDestination() {
        assertThatThrownBy(() -> new SearchCriteria("Mumbai", "mumbai", LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankOriginOrDestination() {
        assertThatThrownBy(() -> new SearchCriteria(" ", "Pune", LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchCriteria("Mumbai", " ", LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
