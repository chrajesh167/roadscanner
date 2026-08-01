package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Federated search across every provider that can serve a route.
 *
 * <p>The command names no provider. Which providers are worth asking is derived from
 * {@code provider_location_mapping}: a provider with a mapping for both endpoints can express the
 * route, and one without cannot. That keeps provider selection data-driven — onboarding a provider
 * makes it searchable by inserting mappings, with no code change and no list to maintain.
 *
 * <p>The command speaks only in {@link LocationId}, the platform's canonical location identity, so
 * no caller ever handles a provider identifier.
 */
public interface SearchProviderTrips {

    Result search(Command command);

    record Command(LocationId origin, LocationId destination, LocalDate travelDate) {
        public Command {
            Objects.requireNonNull(origin, "origin must not be null");
            Objects.requireNonNull(destination, "destination must not be null");
            Objects.requireNonNull(travelDate, "travelDate must not be null");
            if (origin.equals(destination)) {
                throw new IllegalArgumentException("origin and destination must differ");
            }
        }
    }

    /**
     * Aggregated results, plus an honest account of who answered.
     *
     * <p>{@code failed} is reported rather than swallowed. A result set assembled from three
     * providers where one timed out is not the same answer as one where all three succeeded, and a
     * caller that cannot tell them apart will present a partial result as complete.
     *
     * @param queried   providers asked, because they could express the route
     * @param succeeded providers that answered, whether or not they had trips
     * @param failed    providers that could not be reached or refused the request
     */
    record Result(List<ProviderTripResult> trips, Set<ProviderCode> queried, Set<ProviderCode> succeeded,
                  Set<ProviderCode> failed) {
        public Result {
            Objects.requireNonNull(trips, "trips must not be null");
            trips = List.copyOf(trips);
            queried = Set.copyOf(Objects.requireNonNull(queried, "queried must not be null"));
            succeeded = Set.copyOf(Objects.requireNonNull(succeeded, "succeeded must not be null"));
            failed = Set.copyOf(Objects.requireNonNull(failed, "failed must not be null"));
        }

        /** No provider search was performed — the caller supplied no canonical location ids. */
        public static Result empty() {
            return new Result(List.of(), Set.of(), Set.of(), Set.of());
        }

        /** True when every provider asked answered — the result set is complete as of now. */
        public boolean complete() {
            return failed.isEmpty();
        }
    }
}
