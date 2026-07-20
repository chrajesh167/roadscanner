package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.util.Objects;

/** Retrieves the seat layout for one provider trip, served from {@code ProviderCache} when
 * available (short TTL) and falling through to a live provider call on miss. */
public interface GetSeatMap {

    Result getSeatMap(Command command);

    record Command(ProviderSessionId sessionId, String providerTripId) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (providerTripId == null || providerTripId.isBlank()) {
                throw new IllegalArgumentException("providerTripId must not be blank");
            }
        }
    }

    record Result(ProviderSeatMap seatMap) {
        public Result {
            Objects.requireNonNull(seatMap, "seatMap must not be null");
        }
    }
}
