package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusTypeTest {

    @Test
    void rejectsBlankCategory() {
        assertThatThrownBy(() -> new BusType("  ", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amenitiesListIsImmutable() {
        List<String> mutable = new ArrayList<>(List.of("WiFi"));
        BusType busType = new BusType("AC Sleeper", mutable);
        mutable.add("Charging Port");

        assertThat(busType.amenities()).containsExactly("WiFi");
        assertThatThrownBy(() -> busType.amenities().add("Blanket"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
