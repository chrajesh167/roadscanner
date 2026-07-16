package com.roadscanner.authservice.adapter.out.security;

import com.roadscanner.authservice.domain.model.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherAdapterTest {

    // Strength 10 (the floor) in tests — the production default of 12 exists to be slow,
    // which is a feature there and pure waste here.
    private final BCryptPasswordHasherAdapter adapter = new BCryptPasswordHasherAdapter(10);

    @Test
    void hashRoundTripsAndNeverStoresTheRawPassword() {
        PasswordHash hash = adapter.hash("correct-horse-7-battery");

        assertThat(hash.value()).doesNotContain("correct-horse");
        assertThat(hash.algorithmId()).isEqualTo("bcrypt-10");
        assertThat(adapter.matches("correct-horse-7-battery", hash)).isTrue();
        assertThat(adapter.matches("wrong-password-11", hash)).isFalse();
    }

    @Test
    void saltingMakesEqualPasswordsHashDifferently() {
        assertThat(adapter.hash("same-password-9").value())
                .isNotEqualTo(adapter.hash("same-password-9").value());
    }

    @Test
    void matchesWorksAcrossCostFactors() {
        // BCrypt embeds its cost in the hash — an old, cheaper hash must keep verifying after
        // the platform baseline increases (the rehash-on-login upgrade path depends on this).
        PasswordHash oldHash = new BCryptPasswordHasherAdapter(10).hash("legacy-password-3z");
        assertThat(new BCryptPasswordHasherAdapter(11).matches("legacy-password-3z", oldHash)).isTrue();
    }
}
