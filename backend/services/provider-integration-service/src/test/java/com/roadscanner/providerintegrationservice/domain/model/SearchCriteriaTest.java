package com.roadscanner.providerintegrationservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCriteriaTest {

    @Test
    void rejectsSameOriginAndDestination() {
        assertThatThrownBy(() -> new SearchCriteria("58291", "58291", LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        // Casing is significant: these are opaque provider ids, and nothing may assume the system
        // that issued them treats "58291a" and "58291A" as the same place.
        assertThatCode(() -> new SearchCriteria("58291a", "58291A", LocalDate.of(2026, 8, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankOriginOrDestination() {
        assertThatThrownBy(() -> new SearchCriteria(" ", "Pune", LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchCriteria("Mumbai", " ", LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
