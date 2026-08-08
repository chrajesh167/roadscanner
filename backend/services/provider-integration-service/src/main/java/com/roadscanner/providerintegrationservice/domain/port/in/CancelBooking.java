package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.util.Objects;

/** Cancels a confirmed provider order in full and reports what was actually refunded. */
public interface CancelBooking {

    Result cancel(Command command);

    /**
     * The order token is deliberately absent: it is resolved here from the {@code ProviderBooking}
     * recorded at confirmation, not supplied by the caller.
     *
     * <p>It used to be a required field on this command, which meant only a caller already holding
     * a provider credential could cancel — and no caller ever held one, because the token was never
     * returned or stored. Resolving it internally is what makes cancellation reachable at all, and
     * it keeps a provider secret inside the one service whose job is provider vocabulary.
     */
    record Command(ProviderSessionId sessionId, String providerOrderReference, String reason) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (providerOrderReference == null || providerOrderReference.isBlank()) {
                throw new IllegalArgumentException("providerOrderReference must not be blank");
            }
        }
    }

    record Result(CancellationResult cancellation) {
        public Result {
            Objects.requireNonNull(cancellation, "cancellation must not be null");
        }
    }
}
