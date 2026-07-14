package com.roadscanner.authservice.domain.service;

import com.roadscanner.authservice.domain.model.PasswordHash;

import java.util.Objects;

/**
 * Decides whether an already-stored {@link PasswordHash} was produced by an outdated
 * algorithm/cost-factor and should be opportunistically upgraded — the well-known
 * "rehash on next successful login" pattern. Per
 * docs/services/auth-service/security-design.md: "The hashing cost factor is an operational
 * tuning knob, expected to increase over time," so a hash created under yesterday's baseline
 * should not silently remain weaker forever.
 *
 * Deliberately distinct from {@link com.roadscanner.authservice.domain.port.out.PasswordHasher}:
 * that port performs the actual hashing (an infrastructure/crypto-library concern, implemented
 * outside the domain); this is the domain-level policy of whether an existing hash still meets
 * the current bar, which needs no cryptography at all — it only compares algorithm identifiers.
 */
public final class PasswordHashingPolicy {

    private final String currentAlgorithmId;

    private PasswordHashingPolicy(String currentAlgorithmId) {
        Objects.requireNonNull(currentAlgorithmId, "currentAlgorithmId must not be null");
        if (currentAlgorithmId.isBlank()) {
            throw new IllegalArgumentException("currentAlgorithmId must not be blank");
        }
        this.currentAlgorithmId = currentAlgorithmId;
    }

    public static PasswordHashingPolicy withCurrentAlgorithm(String currentAlgorithmId) {
        return new PasswordHashingPolicy(currentAlgorithmId);
    }

    public boolean needsRehash(PasswordHash hash) {
        Objects.requireNonNull(hash, "hash must not be null");
        return !currentAlgorithmId.equals(hash.algorithmId());
    }

    public String currentAlgorithmId() {
        return currentAlgorithmId;
    }
}
