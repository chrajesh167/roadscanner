package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityStatusTest {

    @Test
    void ofCarriesTheSeatCount() {
        AvailabilityStatus status = AvailabilityStatus.of(12);
        assertThat(status.isKnown()).isTrue();
        assertThat(status.seatsAvailable()).hasValue(12);
    }

    @Test
    void unknownCarriesNoCount() {
        AvailabilityStatus status = AvailabilityStatus.unknown();
        assertThat(status.isKnown()).isFalse();
        assertThat(status.seatsAvailable()).isEmpty();
    }

    @Test
    void rejectsNegativeSeatCount() {
        assertThatThrownBy(() -> AvailabilityStatus.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
