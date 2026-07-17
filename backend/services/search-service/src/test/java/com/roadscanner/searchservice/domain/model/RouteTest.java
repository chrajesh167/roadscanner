package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteTest {

    @Test
    void trimsWhitespace() {
        Route route = new Route(" Mumbai ", " Pune ");
        assertThat(route.origin()).isEqualTo("Mumbai");
        assertThat(route.destination()).isEqualTo("Pune");
    }

    @Test
    void rejectsBlankOrigin() {
        assertThatThrownBy(() -> new Route("  ", "Pune")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankDestination() {
        assertThatThrownBy(() -> new Route("Mumbai", "  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdenticalOriginAndDestinationCaseInsensitively() {
        assertThatThrownBy(() -> new Route("Mumbai", "MUMBAI")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValues() {
        assertThatThrownBy(() -> new Route(null, "Pune")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Route("Mumbai", null)).isInstanceOf(NullPointerException.class);
    }
}
