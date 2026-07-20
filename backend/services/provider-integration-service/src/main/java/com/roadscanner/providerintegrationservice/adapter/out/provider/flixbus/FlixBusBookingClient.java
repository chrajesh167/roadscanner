package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** {@code POST /v1/seat-blocks/{blockReference}/booking} — see {@link FlixBusMapper}'s Javadoc
 * for the full documented contract. Resilience4j instance {@code flixbus} (application.yml). */
@Component
class FlixBusBookingClient {

    private static final String CONFIRM_PATH = "/v1/seat-blocks/{blockReference}/booking";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;

    FlixBusBookingClient(RestClient flixBusRestClient, FlixBusMapper mapper, FlixBusExceptionTranslator exceptionTranslator) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "confirmBookingFallback")
    @Retry(name = "flixbus")
    BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference, String providerTripId,
                                        List<PassengerDetail> passengers) {
        try {
            FlixBusMapper.BookingResponseDto response = restClient.post()
                    .uri(CONFIRM_PATH, providerBlockReference)
                    .header("Authorization", session.token().tokenType() + " " + session.token().accessToken())
                    .body(mapper.toConfirmRequest(providerTripId, passengers))
                    .retrieve()
                    .body(FlixBusMapper.BookingResponseDto.class);
            return mapper.toBookingConfirmation(ReservationId.generate(), providerTripId, passengers, response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateConfirm(e);
        }
    }

    @SuppressWarnings("unused")
    private BookingConfirmation confirmBookingFallback(ProviderSession session, String providerBlockReference,
                                                         String providerTripId, List<PassengerDetail> passengers,
                                                         Throwable t) {
        throw exceptionTranslator.translateFallback("confirmBooking", t);
    }
}
