package com.roadscanner.inventoryservice.domain.model;

import java.util.Locale;

/**
 * Identifies which external provider a {@link ProviderMapping} points to — the exact value
 * {@code provider-integration-service}'s own {@code ProviderType} uses (an open, normalized code,
 * not a Java {@code enum}), per docs/services/inventory-service/domain-model.md's
 * {@code ProviderMapping} entry. Onboarding a new provider never requires a change here — this
 * type has no fixed vocabulary, only a normalization rule.
 */
public record ProviderType(String code) {

    public ProviderType {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.strip().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return code;
    }
}
