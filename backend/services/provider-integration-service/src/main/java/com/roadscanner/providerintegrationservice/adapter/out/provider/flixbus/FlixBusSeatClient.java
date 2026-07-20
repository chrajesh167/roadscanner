package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** {@code GET .../seatmap}, {@code POST .../seat-blocks}, {@code DELETE .../seat-blocks/{ref}} —
 * see {@link FlixBusMapper}'s Javadoc for the full documented contract. Resilience4j instance
 * {@code flixbus} (application.yml). */
@Component
class FlixBusSeatClient {

    private static final String SEAT_MAP_PATH = "/v1/trips/{tripId}/seatmap";
    private static final String BLOCK_PATH = "/v1/trips/{tripId}/seat-blocks";
    private static final String RELEASE_PATH = "/v1/seat-blocks/{blockReference}";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;

    FlixBusSeatClient(RestClient flixBusRestClient, FlixBusMapper mapper, FlixBusExceptionTranslator exceptionTranslator) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "getSeatMapFallback")
    @Retry(name = "flixbus")
    ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
        try {
            FlixBusMapper.SeatMapResponseDto response = restClient.get()
                    .uri(SEAT_MAP_PATH, providerTripId)
                    .header("Authorization", bearer(session))
                    .retrieve()
                    .body(FlixBusMapper.SeatMapResponseDto.class);
            return mapper.toProviderSeatMap(providerTripId, response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateSeatMap(e, providerTripId);
        }
    }

    @SuppressWarnings("unused")
    private ProviderSeatMap getSeatMapFallback(ProviderSession session, String providerTripId, Throwable t) {
        throw exceptionTranslator.translateFallback("getSeatMap", t);
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "blockSeatsFallback")
    @Retry(name = "flixbus")
    SeatReservation blockSeats(ProviderSession session, String providerTripId, List<SeatNumber> seatNumbers) {
        try {
            FlixBusMapper.BlockResponseDto response = restClient.post()
                    .uri(BLOCK_PATH, providerTripId)
                    .header("Authorization", bearer(session))
                    .body(mapper.toBlockRequest(seatNumbers))
                    .retrieve()
                    .body(FlixBusMapper.BlockResponseDto.class);
            return mapper.toSeatReservation(providerTripId, response, seatNumbers);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateBlock(e);
        }
    }

    @SuppressWarnings("unused")
    private SeatReservation blockSeatsFallback(ProviderSession session, String providerTripId,
                                                List<SeatNumber> seatNumbers, Throwable t) {
        throw exceptionTranslator.translateFallback("blockSeats", t);
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "releaseSeatsFallback")
    @Retry(name = "flixbus")
    void releaseSeats(ProviderSession session, String providerBlockReference) {
        try {
            restClient.delete()
                    .uri(RELEASE_PATH, providerBlockReference)
                    .header("Authorization", bearer(session))
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.NOT_FOUND, (request, response) -> { })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw exceptionTranslator.translateBlock(e);
        }
    }

    @SuppressWarnings("unused")
    private void releaseSeatsFallback(ProviderSession session, String providerBlockReference, Throwable t) {
        throw exceptionTranslator.translateFallback("releaseSeats", t);
    }

    private String bearer(ProviderSession session) {
        return session.token().tokenType() + " " + session.token().accessToken();
    }
}
