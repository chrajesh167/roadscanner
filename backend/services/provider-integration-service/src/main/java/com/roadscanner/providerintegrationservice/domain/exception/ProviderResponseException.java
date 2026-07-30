package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

/**
 * A provider answered successfully but the body could not be understood — a missing required
 * field, an unparseable payload, a contract change on their side.
 *
 * <p>Not retryable: the same call returns the same unreadable body. Separating it from
 * {@link ProviderUnavailableException} is what makes a silent provider contract change visible —
 * it shows up as its own failure mode rather than being buried in the general unavailability count
 * where it looks like flakiness.
 */
public class ProviderResponseException extends ProviderIntegrationException {

    public ProviderResponseException(ProviderType providerType, String operation, String detail, Throwable cause) {
        super("Provider " + providerType + " returned an unusable response for " + operation,
                new ProviderError(providerType, "PROVIDER_RESPONSE_INVALID",
                        detail == null || detail.isBlank() ? "The provider returned an unusable response" : detail,
                        false),
                cause);
    }
}
