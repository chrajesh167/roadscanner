package com.roadscanner.authservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginIdentifierTest {

    @ParameterizedTest
    @ValueSource(strings = {"traveler@example.com", "+14155550100", "9876543210"})
    void acceptsPlausibleEmailOrPhone(String value) {
        assertThat(new LoginIdentifier(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-identifier", "@missing-local-part.com", "123"})
    void rejectsImplausibleValues(String value) {
        assertThatThrownBy(() -> new LoginIdentifier(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new LoginIdentifier(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void trimsWhitespace() {
        assertThat(new LoginIdentifier("  traveler@example.com  ").value())
                .isEqualTo("traveler@example.com");
    }
}
