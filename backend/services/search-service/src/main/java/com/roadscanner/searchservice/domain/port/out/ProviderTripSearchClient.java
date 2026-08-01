package com.roadscanner.searchservice.domain.port.out;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;

import java.time.LocalDate;
import java.util.List;

/**
 * Outbound port for searching an external provider, via provider-integration-service.
 *
 * <p>This service never calls a provider itself — provider-integration-service is the only one
 * permitted to (docs/architecture/service-boundaries.md). What crosses this port is already
 * normalized: RoadScanner models in, RoadScanner models out. No provider URL, payload, credential
 * or response shape exists on this side of the boundary.
 *
 * <p>The city ids are opaque identifiers read from {@code provider_location_mapping}. This service
 * stores and forwards them; it does not know what they mean, and it never derives one.
 */
public interface ProviderTripSearchClient {

    /**
     * @return normalized trips, best-effort. Returns empty rather than throwing when the provider
     *         is unreachable: a provider outage must degrade the result set, never fail the whole
     *         search — the same "degrade, not fail" rule the availability overlay already follows.
     */
    List<ProviderTripResult> search(ProviderCode provider, String originCityId, String destinationCityId,
                                    LocalDate travelDate);
}
