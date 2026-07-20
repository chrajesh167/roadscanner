package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;

/** The provider rejected an authentication or token-refresh attempt (invalid credentials,
 * revoked refresh token, ...). Not retryable by this service without new credentials. */
public class ProviderAuthenticationException extends ProviderIntegrationException {

    public ProviderAuthenticationException(String message, ProviderError error) {
        super(message, error);
    }

    public ProviderAuthenticationException(String message, ProviderError error, Throwable cause) {
        super(message, error, cause);
    }
}
