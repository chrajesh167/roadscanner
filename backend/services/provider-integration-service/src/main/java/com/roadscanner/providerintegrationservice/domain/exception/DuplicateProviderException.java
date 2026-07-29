package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

/**
 * Registering a provider whose type is already in the registry.
 *
 * <p>Checked in the application layer so the caller gets a precise 409 rather than a raw
 * constraint violation surfacing as a 500. The unique constraint on {@code provider_type} remains
 * the real guarantee — the check is for a good error message, not for correctness under
 * concurrency.
 */
public class DuplicateProviderException extends ProviderIntegrationException {

    private final ProviderType providerType;

    public DuplicateProviderException(ProviderType providerType) {
        super("Provider already registered: " + providerType, null);
        this.providerType = providerType;
    }

    public ProviderType providerType() {
        return providerType;
    }
}
