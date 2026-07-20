package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;

/**
 * Base of every exception this service raises. Every provider-specific error (a FlixBus HTTP
 * error, a timeout, an unparseable response) is translated into one of this hierarchy's concrete
 * subtypes at the adapter boundary (e.g. {@code FlixBusExceptionTranslator}) — application and
 * REST-layer code never sees a provider-specific exception type or a raw {@code RestClientException}.
 */
public abstract class ProviderIntegrationException extends RuntimeException {

    private final ProviderError error;

    protected ProviderIntegrationException(String message, ProviderError error) {
        super(message);
        this.error = error;
    }

    protected ProviderIntegrationException(String message, ProviderError error, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public ProviderError error() {
        return error;
    }
}
