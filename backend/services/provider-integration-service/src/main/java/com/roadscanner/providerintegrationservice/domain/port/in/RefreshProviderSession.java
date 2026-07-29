package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.time.Instant;
import java.util.Objects;

/**
 * Establishes a fresh provider session on demand from the admin console, addressed by registry id.
 *
 * <p>Distinct from {@link RefreshSession}, which exchanges one specific still-active session's
 * token and needs a {@code ProviderSessionId} the admin console has no way to know. This port
 * answers the operational question actually being asked — "give this provider a working session
 * now" — by delegating to {@link AuthenticateProvider}, so credential handling and session
 * persistence stay in exactly one implementation.
 *
 * <p>Useful immediately after rotating credentials: it proves the new secrets authenticate before
 * anyone enables the provider for real traffic.
 */
public interface RefreshProviderSession {

    Result refresh(Command command);

    record Command(ProviderId providerId) {
        public Command {
            Objects.requireNonNull(providerId, "providerId must not be null");
        }
    }

    /**
     * @throws com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException
     *         if no provider with this id exists
     * @throws com.roadscanner.providerintegrationservice.domain.exception.ProviderAuthenticationException
     *         if the provider rejected the configured credentials
     */
    record Result(ProviderSessionId sessionId, Instant expiresAt) {
        public Result {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
