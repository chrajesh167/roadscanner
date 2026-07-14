package com.roadscanner.authservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashTest {

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> new PasswordHash("  ", "bcrypt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankAlgorithmId() {
        assertThatThrownBy(() -> new PasswordHash("hash", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverExposesTheHashValue() {
        PasswordHash hash = new PasswordHash("super-secret-hash", "bcrypt");
        assertThat(hash.toString()).doesNotContain("super-secret-hash");
    }
}
