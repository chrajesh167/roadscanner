package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

    FlixBusSearchClient(RestClient flixBusRestClient, FlixBusMapper mapper, FlixBusExceptionTranslator exceptionTranslator) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "searchFallback")
    @Retry(name = "flixbus")
    List<ProviderTrip> search(ProviderSession session, SearchCriteria criteria) {
        try {
            FlixBusMapper.TripsResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH)
                            .queryParam("origin", criteria.origin())
                            .queryParam("destination", criteria.destination())
                            .queryParam("date", criteria.travelDate())
                            .build())
                    .header("Authorization", session.token().tokenType() + " " + session.token().accessToken())
                    .retrieve()
                    .body(FlixBusMapper.TripsResponseDto.class);
            return mapper.toProviderTrips(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateSearch(e);
        }
    }

    @SuppressWarnings("unused")
    private List<ProviderTrip> searchFallback(ProviderSession session, SearchCriteria criteria, Throwable t) {
        throw exceptionTranslator.translateFallback("search", t);
    }
}
