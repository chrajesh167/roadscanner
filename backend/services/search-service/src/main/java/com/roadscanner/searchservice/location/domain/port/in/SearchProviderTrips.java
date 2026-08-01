package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Searches a provider for trips between two canonical RoadScanner locations.
 *
 * <p>The command speaks only in {@link LocationId} — the platform's canonical location identity.
 * Translating those into whatever ids the provider uses happens inside, against
 * {@code provider_location_mapping}, so no caller ever handles a provider identifier.
 */
public interface SearchProviderTrips {

    Result search(Command command);

    record Command(ProviderCode provider, LocationId origin, LocationId destination, LocalDate travelDate) {
        public Command {
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(origin, "origin must not be null");
            Objects.requireNonNull(destination, "destination must not be null");
            Objects.requireNonNull(travelDate, "travelDate must not be null");
            if (origin.equals(destination)) {
                throw new IllegalArgumentException("origin and destination must differ");
            }
        }
    }

    /**
     * @param mapped false when this provider has no mapping for one or both locations — a normal,
     *               expected answer meaning "this provider does not serve that route", not a
     *               failure. Distinguished from an empty result so a caller can tell "we never
     *               asked" from "we asked and there was nothing".
     */
    record Result(List<ProviderTripResult> trips, boolean mapped) {
        public Result {
            Objects.requireNonNull(trips, "trips must not be null");
            trips = List.copyOf(trips);
        }

        static Result unmapped() {
            return new Result(List.of(), false);
        }
    }
}
