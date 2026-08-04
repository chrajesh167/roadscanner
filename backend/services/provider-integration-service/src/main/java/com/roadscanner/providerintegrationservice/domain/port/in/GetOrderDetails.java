package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.util.Objects;

/** Reads a provider's own view of an order, for display and support lookups. */
public interface GetOrderDetails {

    Result get(Command command);

    record Command(ProviderSessionId sessionId, String providerOrderReference, String providerOrderToken) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (providerOrderReference == null || providerOrderReference.isBlank()) {
                throw new IllegalArgumentException("providerOrderReference must not be blank");
            }
            if (providerOrderToken == null || providerOrderToken.isBlank()) {
                throw new IllegalArgumentException("providerOrderToken must not be blank");
            }
        }
    }

    record Result(ProviderOrder order) {
        public Result {
            Objects.requireNonNull(order, "order must not be null");
        }
    }
}
