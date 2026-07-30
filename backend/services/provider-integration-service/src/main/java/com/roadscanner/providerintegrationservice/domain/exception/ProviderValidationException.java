package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

/**
 * A provider rejected the request as invalid — a malformed date, an unknown station code, a
 * passenger detail it will not accept.
 *
 * <p><strong>Not retryable, and that is the entire point of the type.</strong> The request will be
 * rejected identically every time, so retrying burns the provider's rate limit and our own request
 * threads to arrive at the same answer more slowly. Without a distinct type these arrive as
 * generic failures and get retried by default.
 */
public class ProviderValidationException extends ProviderIntegrationException {

    public ProviderValidationException(ProviderType providerType, String operation, String detail, Throwable cause) {
        super("Provider " + providerType + " rejected the " + operation + " request as invalid",
                new ProviderError(providerType, "PROVIDER_VALIDATION_FAILED",
                        detail == null || detail.isBlank() ? "The provider rejected the request as invalid" : detail,
                        false),
                cause);
    }
}
