package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchQueryTest {

    private static final Route ROUTE = new Route("Mumbai", "Pune");
    private static final LocalDate DATE = LocalDate.parse("2026-08-01");

    @Test
    void acceptsAMinimalValidQuery() {
        assertThatCode(() -> new SearchQuery(ROUTE, DATE, null, null, null, null, null, null, null, 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, null, null, null, null, null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSizeOutsideAbsoluteBounds() {
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, null, null, null, null, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, null, null, null, null, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinFareGreaterThanMaxFare() {
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, new BigDecimal("500"), new BigDecimal("100"),
                null, null, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFareBounds() {
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, new BigDecimal("-1"), null,
                null, null, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDepartureAfterNotBeforeDepartureBefore() {
        Instant instant = Instant.parse("2026-08-01T10:00:00Z");
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, null, null, instant, instant,
                null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRatingOutsideZeroToFive() {
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, null, null, null, null, null, -0.1, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchQuery(ROUTE, DATE, null, null, null, null, null, 5.1, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankBusTypeCategoryIsNormalizedToNull() {
        SearchQuery query = new SearchQuery(ROUTE, DATE, null, null, null, null, "   ", null, null, 0, 20);
        assertThat(query.busTypeCategory()).isNull();
    }
}
