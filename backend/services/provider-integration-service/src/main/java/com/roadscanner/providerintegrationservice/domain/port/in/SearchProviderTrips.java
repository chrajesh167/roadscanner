package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;

import java.util.List;
import java.util.Objects;

/**
 * Searches one provider for trips — the inbound port behind
 * {@code GET /internal/api/v1/providers/{providerType}/trips}.
 *
 * <p><strong>Deliberately session-less.</strong> Whether a provider needs an authenticated session
 * to search is that provider's business: some issue a static partner credential that is sufficient
 * on its own, others require a login first. Putting a {@code ProviderSessionId} in this command
 * would impose the second model on every provider, forcing a login before searches that do not
 * need one — a wasted round trip, an unnecessary failure mode, and a session to keep alive for no
 * reason.
 *
 * <p>The adapter resolves whatever authentication it actually requires, from
 * {@code provider_credentials} and, where relevant, its own {@code ProviderSession}. The generic
 * layer does not model that difference because it does not need to.
 *
 * <p>Distinct from {@link SearchTrips}, which remains for callers that already hold a session and
 * want the search bound to it.
 */
public interface SearchProviderTrips {

    Result search(Command command);

    record Command(ProviderType providerType, SearchCriteria criteria) {
        public Command {
            Objects.requireNonNull(providerType, "providerType must not be null");
            Objects.requireNonNull(criteria, "criteria must not be null");
        }
    }

    /**
     * @throws com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException
     *         if the provider has no adapter, is disabled, or does not support search
     */
    record Result(List<ProviderTrip> trips) {
        public Result {
            Objects.requireNonNull(trips, "trips must not be null");
            trips = List.copyOf(trips);
        }
    }
}
