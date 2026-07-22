package com.roadscanner.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderTypeTest {

    @Test
    void normalizesToUppercase() {
        assertThat(new ProviderType("mock")).isEqualTo(new ProviderType("MOCK"));
        assertThat(new ProviderType(" flixbus ").code()).isEqualTo("FLIXBUS");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new ProviderType(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
