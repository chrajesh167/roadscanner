package com.roadscanner.searchservice.adapter.out.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p><strong>Degrades rather than fails.</strong> A provider outage returns an empty list and a
 * warning, matching how {@code InventoryAvailabilityClientAdapter} handles an unreachable
 * inventory-service: a traveller searching for trips should still see first-party results when one
 * provider is down, not an error page.
 */
@Component
class ProviderIntegrationSearchClientAdapter implements ProviderTripSearchClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderIntegrationSearchClientAdapter.class);
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
            // Degrade, never fail: one provider being unreachable must not empty the whole result.
            log.warn("Provider search failed for provider={} — continuing without its results", provider, e);
            return List.of();
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
