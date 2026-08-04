package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * A provider's order as the provider describes it.
 *
 * <p>{@code details} is deliberately an opaque map rather than a typed structure. Order payloads
 * differ per provider and are used for display and support lookups, not for decisions this service
 * makes — modelling them would mean inventing a shape none of them actually share, and would have
 * to change every time a provider added a field.
 */
public record ProviderOrder(String providerOrderReference, ProviderType providerType, Map<String, Object> details) {

    public ProviderOrder {
        if (providerOrderReference == null || providerOrderReference.isBlank()) {
            throw new IllegalArgumentException("providerOrderReference must not be blank");
        }
        providerOrderReference = providerOrderReference.strip();
        Objects.requireNonNull(providerType, "providerType must not be null");
        details = Map.copyOf(Objects.requireNonNullElseGet(details, Map::of));
    }
}
