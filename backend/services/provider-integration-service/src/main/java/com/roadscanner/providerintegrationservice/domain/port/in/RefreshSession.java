package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.time.Instant;
import java.util.Objects;

/** Exchanges a still-{@code ACTIVE} session's token for a fresh one before it expires. Raises
 * {@link com.roadscanner.providerintegrationservice.domain.exception.SessionExpiredException} if
 * the session is already terminal — the caller must {@link AuthenticateProvider} again instead. */
public interface RefreshSession {

    Result refresh(Command command);

    record Command(ProviderSessionId sessionId) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
        }
    }

    record Result(ProviderSessionId sessionId, Instant expiresAt) {
        public Result {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
