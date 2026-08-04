package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Endpoint 3 — {@code GET /public/v3/ancillaries/seat-map}.
 *
 * <p>Takes the three UUIDs carried by the trip uid ({@link FlixBusTripUid}), so a seat map can be
 * fetched from a search result alone with nothing looked up in between.
 *
 * <p>Partner token only; no {@code X-API-Session} is documented for this call.
 */
@Component
class FlixBusSeatMapClient {

    static final String SEAT_MAP_PATH = "/public/v3/ancillaries/seat-map";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusCredentials credentials;
    private final FlixBusProperties properties;

    FlixBusSeatMapClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                         FlixBusExceptionTranslator exceptionTranslator, FlixBusCredentials credentials,
                         FlixBusProperties properties) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.credentials = credentials;
        this.properties = properties;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "seatMapFallback")
    ProviderSeatMap getSeatMap(Provider provider, FlixBusTripUid tripUid, java.math.BigDecimal tripBaseFare) {
        try {
            FlixBusMapper.SeatMapResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEAT_MAP_PATH)
                            .queryParam("ride_id", tripUid.rideId())
                            .queryParam("departure_station_id", tripUid.fromStationId())
                            .queryParam("arrival_station_id", tripUid.toStationId())
                            .queryParam("currency", properties.currency())
                            .build())
                    .header(FlixBusCredentials.AUTHENTICATION_HEADER, credentials.partnerToken(provider))
                    .retrieve()
                    .body(FlixBusMapper.SeatMapResponseDto.class);

            return mapper.toProviderSeatMap(response, tripUid, tripBaseFare, properties.currency());
        } catch (RestClientException e) {
            throw exceptionTranslator.translateSeatMap(e, tripUid.value());
        }
    }

    /**
     * The per-seat provider ids for this ride, keyed by the label a caller names a seat by.
     *
     * <p>Same endpoint, different projection: reserving needs the ids, displaying needs the map.
     * Exposed separately so the booking flow does not have to re-derive one from the other.
     */
    @CircuitBreaker(name = "flixbus", fallbackMethod = "seatIdsFallback")
    java.util.Map<String, String> seatIdsByLabel(Provider provider, FlixBusTripUid tripUid) {
        try {
            FlixBusMapper.SeatMapResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEAT_MAP_PATH)
                            .queryParam("ride_id", tripUid.rideId())
                            .queryParam("departure_station_id", tripUid.fromStationId())
                            .queryParam("arrival_station_id", tripUid.toStationId())
                            .queryParam("currency", properties.currency())
                            .build())
                    .header(FlixBusCredentials.AUTHENTICATION_HEADER, credentials.partnerToken(provider))
                    .retrieve()
                    .body(FlixBusMapper.SeatMapResponseDto.class);

            return mapper.toSeatIdsByLabel(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateSeatMap(e, tripUid.value());
        }
    }

    @SuppressWarnings("unused")
    private java.util.Map<String, String> seatIdsFallback(Provider provider, FlixBusTripUid tripUid, Throwable t) {
        throw exceptionTranslator.translateFallback("getSeatMap", t);
    }

    @SuppressWarnings("unused")
    private ProviderSeatMap seatMapFallback(Provider provider, FlixBusTripUid tripUid,
                                            java.math.BigDecimal tripBaseFare, Throwable t) {
        throw exceptionTranslator.translateFallback("getSeatMap", t);
    }
}
