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
     * Searches one provider.
     *
     * <p><strong>Throws when the provider could not be searched.</strong> Degradation is the
     * caller's job, not this port's: {@code SearchProviderTripsService} already catches per
     * provider, records the failure and continues with the others, so an outage still degrades the
     * result set rather than failing the search.
     *
     * <p>This port previously promised to return empty rather than throw. That made a failure
     * indistinguishable from a provider with no trips on the route, and left the federation unable
     * to report a partial answer as partial — both layers degraded, and the signal cancelled out.
     * Reporting the failure once, here, and handling it once, there, keeps exactly one place
     * responsible for each.
     *
     * @return normalized trips; empty is a legitimate answer meaning the provider served none
     * @throws com.roadscanner.searchservice.location.domain.exception.ProviderSearchFailedException
     *         if the provider could not be reached or returned an unusable response
     */
    List<ProviderTripResult> search(ProviderCode provider, String originCityId, String destinationCityId,
                                    LocalDate travelDate);
}
