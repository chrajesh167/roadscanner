package com.roadscanner.authservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserIdTest {

    @Test
    void generateProducesUniqueIds() {
        assertThat(UserId.generate()).isNotEqualTo(UserId.generate());
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new UserId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalityIsByValue() {
        UUID uuid = UUID.randomUUID();
        assertThat(new UserId(uuid)).isEqualTo(new UserId(uuid));
    }
}
