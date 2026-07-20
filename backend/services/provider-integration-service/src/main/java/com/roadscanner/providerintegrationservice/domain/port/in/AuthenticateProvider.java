package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.time.Instant;
import java.util.Objects;

/** Opens a new {@link com.roadscanner.providerintegrationservice.domain.model.ProviderSession}
 * against the named provider. The first call in every provider interaction — every other
 * inbound port takes the resulting {@link ProviderSessionId} as input. */
public interface AuthenticateProvider {

    Result authenticate(Command command);

    record Command(ProviderType providerType) {
        public Command {
            Objects.requireNonNull(providerType, "providerType must not be null");
        }
    }

    record Result(ProviderSessionId sessionId, ProviderType providerType, Instant expiresAt) {
        public Result {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(providerType, "providerType must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
