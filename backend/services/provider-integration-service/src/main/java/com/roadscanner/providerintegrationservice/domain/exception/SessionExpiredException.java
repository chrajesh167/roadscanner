package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

/** The caller presented a {@link ProviderSessionId} that is no longer {@code ACTIVE} (expired or
 * revoked). The caller must authenticate again — sessions never resurrect. */
public class SessionExpiredException extends ProviderIntegrationException {

    private final ProviderSessionId sessionId;

    public SessionExpiredException(ProviderSessionId sessionId) {
        super("Provider session is no longer active: " + sessionId, null);
        this.sessionId = sessionId;
    }

    public ProviderSessionId sessionId() {
        return sessionId;
    }
}
