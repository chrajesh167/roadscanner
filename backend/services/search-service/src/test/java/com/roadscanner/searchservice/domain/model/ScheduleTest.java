package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleTest {

    private static final Instant DEPARTURE = Instant.parse("2026-08-01T08:00:00Z");

    @Test
    void computesDurationFromTimestamps() {
        Schedule schedule = new Schedule(DEPARTURE, DEPARTURE.plus(Duration.ofHours(5)));
        assertThat(schedule.duration()).isEqualTo(Duration.ofHours(5));
    }

    @Test
    void rejectsArrivalNotAfterDeparture() {
        assertThatThrownBy(() -> new Schedule(DEPARTURE, DEPARTURE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Schedule(DEPARTURE, DEPARTURE.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
