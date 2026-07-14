package com.roadscanner.authservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHashTest {

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> new TokenHash(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverExposesTheHashValue() {
        assertThat(new TokenHash("super-secret-hash").toString()).doesNotContain("super-secret-hash");
    }
}
