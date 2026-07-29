package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderId;

/**
 * An admin operation referenced a provider that is not in the registry.
 *
 * <p>Distinct from {@link ProviderNotSupportedException}, which means "this provider exists but
 * has no adapter / is disabled". Collapsing them would hide the difference between a typo in an
 * id and a provider that is genuinely switched off.
 */
public class ProviderNotFoundException extends ProviderIntegrationException {

    private final String reference;

    public ProviderNotFoundException(ProviderId providerId) {
        super("No such provider: " + providerId, null);
        this.reference = providerId.toString();
    }

    public ProviderNotFoundException(String reference) {
        super("No such provider: " + reference, null);
        this.reference = reference;
    }

    public String reference() {
        return reference;
    }
}
