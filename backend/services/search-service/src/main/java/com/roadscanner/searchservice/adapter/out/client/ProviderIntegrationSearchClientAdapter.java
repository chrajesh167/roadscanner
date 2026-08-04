package com.roadscanner.searchservice.adapter.out.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.domain.exception.ProviderSearchFailedException;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/**
 * Calls provider-integration-service's generic search route and maps its already-normalized
 * response into this service's own models.
 *
 * <p>The only provider-related outbound call this service makes, and it is to RoadScanner's own
 * service — never to a provider. The wire format below is provider-integration-service's canonical
 * contract, identical for every provider, so onboarding a new one changes nothing here.
 *
 * <p><strong>Reports failures; does not absorb them.</strong> A provider outage throws
 * {@link ProviderSearchFailedException}, which the federation catches per provider so a traveller
 * still sees first-party results and every working provider's trips. That is a deliberate departure
 * from {@code InventoryAvailabilityClientAdapter}, which absorbs its own failure: that overlay has
 * a value meaning "unknown" to fall back to, whereas an empty trip list is indistinguishable from a
 * provider that genuinely serves no trips.
 */
@Component
class ProviderIntegrationSearchClientAdapter implements ProviderTripSearchClient {

    private static final String SEARCH_PATH = "/internal/api/v1/providers/{providerType}/trips";

    private final RestClient restClient;

    ProviderIntegrationSearchClientAdapter(RestClient providerIntegrationRestClient) {
        this.restClient = providerIntegrationRestClient;
    }

    @Override
    public List<ProviderTripResult> search(ProviderCode provider, String originCityId, String destinationCityId,
                                           LocalDate travelDate) {
        try {
            SearchTripsResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH)
                            .queryParam("fromCityId", originCityId)
                            .queryParam("toCityId", destinationCityId)
                            .queryParam("departureDate", travelDate)
                            .build(provider.value()))
                    .retrieve()
                    .body(SearchTripsResponseDto.class);

            if (response == null || response.trips() == null) {
                return List.of();
            }
            return response.trips().stream().map(dto -> toResult(provider, dto)).toList();
        } catch (RestClientException e) {
            // Reported, not hidden. Swallowing this here returned the same empty list as a provider
            // that simply serves no trips on the route, which is why a failed search was being
            // presented to callers as a complete one. The federation catches this, marks the
            // provider failed, and still returns every other provider's results.
            throw new ProviderSearchFailedException(provider.value(), e);
        }
    }

    private static ProviderTripResult toResult(ProviderCode provider, ProviderTripDto dto) {
        return new ProviderTripResult(
                provider.value(),
                dto.providerTripId(),
                dto.operatorName(),
                new Route(dto.origin(), dto.destination()),
                new Schedule(dto.departureTime(), dto.arrivalTime()),
                dto.serviceClass(),
                new FareSnapshot(dto.fareAmount(), Currency.getInstance(dto.fareCurrency())),
                dto.seatsAvailable(),
                dto.boardingPointId(),
                dto.alightingPointId());
    }

    /**
     * provider-integration-service's canonical response. Package-private and
     * {@code ignoreUnknown}: fields added there must not break search for every user here.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchTripsResponseDto(List<ProviderTripDto> trips) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProviderTripDto(String providerTripId, String providerType, String operatorName, String origin,
                           String destination, Instant departureTime, Instant arrivalTime, String serviceClass,
                           BigDecimal fareAmount, String fareCurrency, int seatsAvailable,
                           String boardingPointId, String alightingPointId) {
    }
}
