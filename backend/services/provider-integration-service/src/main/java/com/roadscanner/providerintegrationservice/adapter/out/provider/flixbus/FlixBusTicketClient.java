package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@code GET /v1/bookings/{bookingReference}/ticket} — see {@link FlixBusMapper}'s Javadoc for
 * the full documented contract. Resilience4j instance {@code flixbus} (application.yml). */
@Component
class FlixBusTicketClient {

    private static final String TICKET_PATH = "/v1/bookings/{bookingReference}/ticket";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;

    FlixBusTicketClient(RestClient flixBusRestClient, FlixBusMapper mapper, FlixBusExceptionTranslator exceptionTranslator) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "downloadTicketFallback")
    @Retry(name = "flixbus")
    ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
        try {
            FlixBusMapper.TicketResponseDto response = restClient.get()
                    .uri(TICKET_PATH, bookingReference.value())
                    .header("Authorization", session.token().tokenType() + " " + session.token().accessToken())
                    .retrieve()
                    .body(FlixBusMapper.TicketResponseDto.class);
            return mapper.toProviderTicket(bookingReference, response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateTicket(e, bookingReference);
        }
    }

    @SuppressWarnings("unused")
    private ProviderTicket downloadTicketFallback(ProviderSession session, BookingReference bookingReference, Throwable t) {
        throw exceptionTranslator.translateFallback("downloadTicket", t);
    }
}
