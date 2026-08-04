package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.util.Objects;

/** Cancels a confirmed provider order in full and reports what was actually refunded. */
public interface CancelBooking {

    Result cancel(Command command);

    /**
     * The order token is required, not optional. Providers authorise cancellation with it, and a
     * caller that has lost it cannot cancel — better to refuse here than to send an empty token and
     * read the provider's rejection as "cancellation failed".
     */
    record Command(ProviderSessionId sessionId, String providerOrderReference, String providerOrderToken,
                   String reason) {
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

    record Result(CancellationResult cancellation) {
        public Result {
            Objects.requireNonNull(cancellation, "cancellation must not be null");
        }
    }
}
