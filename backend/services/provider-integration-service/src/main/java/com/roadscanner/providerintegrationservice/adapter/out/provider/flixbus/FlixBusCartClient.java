package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderResponseException;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SeatAssignment;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Endpoints 4–6 — the three calls that together make up a seat hold:
 * {@code POST /cart/v1/cart}, {@code POST /cart/v1/cart/{cartId}/tickets},
 * {@code PUT /cart/v1/cart/{cartId}/seat-reservation}.
 *
 * <p>They live in one class because they are one operation from the platform's point of view: a
 * cart with no tickets, or tickets with no seats, is not a hold anyone can confirm or release.
 * Splitting them across classes would let a caller stop halfway and leave a half-built cart that
 * nothing owns.
 *
 * <p>All three require {@code X-API-Session} in addition to the partner token.
 */
@Component
class FlixBusCartClient {

    static final String CART_PATH = "/cart/v1/cart";
    static final String TICKETS_PATH = "/cart/v1/cart/{cartId}/tickets";
    static final String SEAT_RESERVATION_PATH = "/cart/v1/cart/{cartId}/seat-reservation";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusRequestContext context;
    private final FlixBusProperties properties;

    FlixBusCartClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                      FlixBusExceptionTranslator exceptionTranslator, FlixBusRequestContext context,
                      FlixBusProperties properties) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.context = context;
        this.properties = properties;
    }

    /** Endpoint 4. Returns the cart id, which becomes the platform's block reference. */
    @CircuitBreaker(name = "flixbus", fallbackMethod = "createCartFallback")
    String createCart(Provider provider) {
        try {
            FlixBusMapper.CartResponseDto response = restClient.post()
                    .uri(CART_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .body(new FlixBusMapper.CreateCartRequestDto(properties.currency()))
                    .retrieve()
                    .body(FlixBusMapper.CartResponseDto.class);

            return mapper.toCartId(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateBlock(e);
        }
    }

    /**
     * Endpoint 5. Returns one ticket id per passenger requested.
     *
     * <p>The response carries mixed item types; only {@code product.type == "ticket"} entries are
     * ticket handles.
     */
    @CircuitBreaker(name = "flixbus", fallbackMethod = "addTicketsFallback")
    List<String> addTickets(Provider provider, String cartId, FlixBusTripUid tripUid, int adults, int children) {
        try {
            FlixBusMapper.CartItemsResponseDto response = restClient.post()
                    .uri(TICKETS_PATH, cartId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .body(mapper.toAddTicketsRequest(tripUid, adults, children))
                    .retrieve()
                    .body(FlixBusMapper.CartItemsResponseDto.class);

            List<String> ticketIds = mapper.toTicketIds(response);
            int expected = adults + children;
            if (ticketIds.size() != expected) {
                // Pairing tickets to seats positionally below only holds if the counts match.
                // Continuing with a mismatch would attach a seat to the wrong traveller's ticket.
                throw new ProviderResponseException(ProviderType.FLIXBUS, "addTickets",
                        "Expected " + expected + " ticket(s) but the cart returned " + ticketIds.size(), null);
            }
            return ticketIds;
        } catch (RestClientException e) {
            throw exceptionTranslator.translateBlock(e);
        }
    }

    /**
     * Endpoint 6. Binds each ticket to one seat and returns the provider's authoritative total.
     *
     * <p>Single attempt by policy — the execution layer never retries a seat block, because a
     * timed-out reservation may already hold the seats.
     */
    @CircuitBreaker(name = "flixbus", fallbackMethod = "reserveSeatsFallback")
    BigDecimal reserveSeats(Provider provider, String cartId, FlixBusTripUid tripUid,
                            List<SeatAssignment> assignments, List<PassengerDetail> passengers) {
        try {
            FlixBusMapper.SeatReservationResponseDto response = restClient.put()
                    .uri(SEAT_RESERVATION_PATH, cartId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .body(mapper.toSeatReservationRequest(tripUid, assignments, passengers))
                    .retrieve()
                    .body(FlixBusMapper.SeatReservationResponseDto.class);

            return mapper.toReservedTotal(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateBlock(e);
        }
    }

    @SuppressWarnings("unused")
    private String createCartFallback(Provider provider, Throwable t) {
        throw exceptionTranslator.translateFallback("createCart", t);
    }

    @SuppressWarnings("unused")
    private List<String> addTicketsFallback(Provider provider, String cartId, FlixBusTripUid tripUid, int adults,
                                            int children, Throwable t) {
        throw exceptionTranslator.translateFallback("addTickets", t);
    }

    @SuppressWarnings("unused")
    private BigDecimal reserveSeatsFallback(Provider provider, String cartId, FlixBusTripUid tripUid,
                                            List<SeatAssignment> assignments, List<PassengerDetail> passengers,
                                            Throwable t) {
        throw exceptionTranslator.translateFallback("reserveSeats", t);
    }
}
