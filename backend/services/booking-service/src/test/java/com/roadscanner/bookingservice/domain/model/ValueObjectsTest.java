package com.roadscanner.bookingservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Test
    void providerTypeNormalizesToUppercase() {
        assertThat(new ProviderType("mock")).isEqualTo(new ProviderType("MOCK"));
        assertThat(new ProviderType(" flixbus ").code()).isEqualTo("FLIXBUS");
    }

    @Test
    void providerTypeRejectsBlank() {
        assertThatThrownBy(() -> new ProviderType(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passengerRejectsImplausibleAge() {
        assertThatThrownBy(() -> new Passenger("Jane Doe", 0, "F", "L1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Passenger("Jane Doe", 121, "F", "L1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passengerRejectsBlankFields() {
        assertThatThrownBy(() -> new Passenger(" ", 30, "F", "L1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Passenger("Jane Doe", 30, "F", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fareRejectsNegativeAmount() {
        assertThatThrownBy(() -> new Fare(BigDecimal.valueOf(-1), Currency.getInstance("INR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requesterContextIsPrivilegedOnlyForAdminAndSupport() {
        java.util.UUID id = java.util.UUID.randomUUID();
        assertThat(new RequesterContext(id, Role.ADMIN).isPrivileged()).isTrue();
        assertThat(new RequesterContext(id, Role.SUPPORT).isPrivileged()).isTrue();
        assertThat(new RequesterContext(id, Role.TRAVELER).isPrivileged()).isFalse();
        assertThat(new RequesterContext(id, Role.OPERATOR).isPrivileged()).isFalse();
    }
}
