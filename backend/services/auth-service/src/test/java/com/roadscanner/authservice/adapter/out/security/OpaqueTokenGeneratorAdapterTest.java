package com.roadscanner.authservice.adapter.out.security;

import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokenGeneratorAdapterTest {

    private final OpaqueTokenGeneratorAdapter adapter = new OpaqueTokenGeneratorAdapter();

    @Test
    void generatedTokensAreUniqueAndCarryTheirOwnHash() {
        Set<String> rawValues = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            TokenGenerator.GeneratedToken token = adapter.generate();
            assertThat(rawValues.add(token.rawValue())).isTrue();
            assertThat(token.tokenHash()).isEqualTo(adapter.hashOf(token.rawValue()));
        }
    }

    @Test
    void hashIsDeterministicAndOneWay() {
        TokenGenerator.GeneratedToken token = adapter.generate();

        assertThat(adapter.hashOf(token.rawValue())).isEqualTo(adapter.hashOf(token.rawValue()));
        assertThat(token.tokenHash().value()).isNotEqualTo(token.rawValue());
        // SHA-256 hex — 64 chars, so the stored column never sees the raw value's alphabet.
        assertThat(token.tokenHash().value()).matches("[0-9a-f]{64}");
    }
}
