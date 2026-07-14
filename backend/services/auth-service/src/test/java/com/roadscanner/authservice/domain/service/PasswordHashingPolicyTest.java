package com.roadscanner.authservice.domain.service;

import com.roadscanner.authservice.domain.model.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashingPolicyTest {

    private final PasswordHashingPolicy policy = PasswordHashingPolicy.withCurrentAlgorithm("argon2id-v2");

    @Test
    void doesNotNeedRehashWhenAlgorithmMatchesCurrent() {
        PasswordHash hash = new PasswordHash("hash-value", "argon2id-v2");
        assertThat(policy.needsRehash(hash)).isFalse();
    }

    @Test
    void needsRehashWhenAlgorithmIsOutdated() {
        PasswordHash hash = new PasswordHash("hash-value", "bcrypt-2b-cost10");
        assertThat(policy.needsRehash(hash)).isTrue();
    }

    @Test
    void rejectsBlankCurrentAlgorithmId() {
        assertThatThrownBy(() -> PasswordHashingPolicy.withCurrentAlgorithm(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
