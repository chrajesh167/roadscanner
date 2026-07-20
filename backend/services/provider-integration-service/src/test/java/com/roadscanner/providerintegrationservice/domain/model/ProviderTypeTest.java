package com.roadscanner.providerintegrationservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderTypeTest {

    @Test
    void normalizesToUppercase() {
        assertThat(new ProviderType("flixbus")).isEqualTo(ProviderType.FLIXBUS);
        assertThat(new ProviderType(" Mock ")).isEqualTo(ProviderType.MOCK);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new ProviderType(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderType(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
