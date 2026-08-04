package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Endpoint 2 — {@code GET /public/v2/trip/search}.
 *
 * <p>Authenticated by the static partner token alone; the documented contract needs no
 * {@code X-API-Session} here, so search never forces a partner login.
 *
 * <p>{@code from_city_id}/{@code to_city_id} are FlixBus's own city UUIDs, resolved by the caller
 * from {@code provider_location_mapping}. This client never translates a place name — it receives
 * ids it treats as opaque.
 */
@Component
class FlixBusSearchClient {

    static final String SEARCH_PATH = "/public/v2/trip/search";

    /** The documented departure-date format. Not ISO — FlixBus expects {@code dd.MM.yyyy}. */
    private static final DateTimeFormatter DEPARTURE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    /**
     * Search is quoted for a single adult, no children, no bikes.
     *
     * <p>{@link SearchCriteria} carries no party composition and the documented parameters are
     * mandatory, so a value has to be chosen. One adult is the honest default: it is the only
     * composition whose returned fare is unambiguously correct as displayed. Quoting for a guessed
     * larger party would show a total nobody asked for. Party-aware search means extending
     * {@code SearchCriteria} platform-wide — a change affecting every provider, not this class.
     */
    private static final int DEFAULT_ADULTS = 1;
    private static final int DEFAULT_CHILDREN = 0;
    private static final int DEFAULT_BIKES = 0;

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusCredentials credentials;
    private final FlixBusProperties properties;

    FlixBusSearchClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                        FlixBusExceptionTranslator exceptionTranslator, FlixBusCredentials credentials,
                        FlixBusProperties properties) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.credentials = credentials;
        this.properties = properties;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "searchFallback")
    List<ProviderTrip> search(Provider provider, SearchCriteria criteria) {
        try {
            FlixBusMapper.TripSearchResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH)
                            .queryParam("from_city_id", criteria.originCityId())
                            .queryParam("to_city_id", criteria.destinationCityId())
                            .queryParam("departure_date", DEPARTURE_DATE.format(criteria.travelDate()))
                            .queryParam("search_by", "cities")
                            .queryParam("adult", DEFAULT_ADULTS)
                            .queryParam("children", DEFAULT_CHILDREN)
                            .queryParam("bikes", DEFAULT_BIKES)
                            .queryParam("currency", properties.currency())
                            .build())
                    .header(FlixBusCredentials.AUTHENTICATION_HEADER, credentials.partnerToken(provider))
                    .retrieve()
                    .body(FlixBusMapper.TripSearchResponseDto.class);

            return mapper.toProviderTrips(response, properties.currency());
        } catch (RestClientException e) {
            throw exceptionTranslator.translateSearch(e);
        }
    }

    @SuppressWarnings("unused")
    private List<ProviderTrip> searchFallback(Provider provider, SearchCriteria criteria, Throwable t) {
        throw exceptionTranslator.translateFallback("search", t);
    }
}
