package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

/** Raised by {@link com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry}
 * when asked to resolve a {@link ProviderType} with no registered {@code ProviderClient} adapter
 * (or that adapter doesn't declare the requested capability) — the extensibility mechanism's
 * failure mode: a provider must be both configured and have a matching adapter bean to be usable. */
public class ProviderNotSupportedException extends ProviderIntegrationException {

    private final ProviderType providerType;

    public ProviderNotSupportedException(ProviderType providerType) {
        super("No provider adapter is registered for " + providerType, null);
        this.providerType = providerType;
    }

    public ProviderType providerType() {
        return providerType;
    }
}
