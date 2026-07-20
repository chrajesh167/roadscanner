package com.roadscanner.providerintegrationservice.domain.model;

/**
 * The canonical shape every provider-specific error is translated into — by, e.g.,
 * {@code FlixBusExceptionTranslator} — before it ever reaches application/use-case code. Carried
 * inside every {@link com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException}
 * subtype so no caller of this service ever needs to understand a specific provider's own error
 * codes or HTTP status conventions.
 */
public record ProviderError(ProviderType providerType, String code, String message, boolean retryable) {

    public ProviderError {
        java.util.Objects.requireNonNull(providerType, "providerType must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
