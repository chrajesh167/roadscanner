package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** {@code GET /v1/trips?origin=&destination=&date=} — see {@link FlixBusMapper}'s Javadoc for
 * the full documented contract. Resilience4j instance {@code flixbus} (application.yml). */
@Component
class FlixBusSearchClient {

    private static final String SEARCH_PATH = "/v1/trips";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusCredentials credentials;

    FlixBusSearchClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                        FlixBusExceptionTranslator exceptionTranslator, FlixBusCredentials credentials) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.credentials = credentials;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "searchFallback")
    // Authenticated with the static partner token from the encrypted credential store — the only
    // header FlixBus requires for search. No session is established: X-API-Session applies to
    // authenticated operations (cart, checkout, booking) and is Sprint 4's work.
    //
    // ASSUMPTION, inherited from an earlier sprint and NOT from the supplied contract: the request
    // path and query shape below, and the response DTO it binds. The real search binding is
    // deliberately deferred until the actual response schema is available — see the sprint notes.
    List<ProviderTrip> search(Provider provider, SearchCriteria criteria) {
        try {
            FlixBusMapper.TripsResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH)
                            .queryParam("origin", criteria.originCityId())
                            .queryParam("destination", criteria.destinationCityId())
                            .queryParam("date", criteria.travelDate())
                            .build())
                    .header(FlixBusCredentials.AUTHENTICATION_HEADER, credentials.partnerToken(provider))
                    .retrieve()
                    .body(FlixBusMapper.TripsResponseDto.class);
            return mapper.toProviderTrips(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateSearch(e);
        }
    }

    @SuppressWarnings("unused")
    private List<ProviderTrip> searchFallback(Provider provider, SearchCriteria criteria, Throwable t) {
        throw exceptionTranslator.translateFallback("search", t);
    }
}
