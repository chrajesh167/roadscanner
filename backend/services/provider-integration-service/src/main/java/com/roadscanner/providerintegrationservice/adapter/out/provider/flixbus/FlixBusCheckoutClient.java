package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Endpoints 7 and 8 — {@code POST /checkout/v1/checkout} and
 * {@code GET /checkout/v1/checkout/{checkoutId}}.
 *
 * <p>Checkout returns a <strong>checkout id, not an order id</strong>. The order is materialized
 * asynchronously, so the same read serves two purposes: verifying the fare before payment, and
 * polling afterwards until {@code order.id} and {@code order.token} appear.
 *
 * <p>The API reference notes that the current reference implementation omits the authentication
 * headers on the read, and says explicitly to send them anyway when building fresh. Both headers
 * are sent here — a call that happens to work unauthenticated today is not a contract, and relying
 * on it would break silently the day FlixBus tightened it.
 */
@Component
class FlixBusCheckoutClient {

    static final String CHECKOUT_PATH = "/checkout/v1/checkout";
    static final String CHECKOUT_BY_ID_PATH = "/checkout/v1/checkout/{checkoutId}";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusRequestContext context;
    private final FlixBusProperties properties;

    FlixBusCheckoutClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                          FlixBusExceptionTranslator exceptionTranslator, FlixBusRequestContext context,
                          FlixBusProperties properties) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.context = context;
        this.properties = properties;
    }

    /** Endpoint 7. Returns the checkout id — a provisional hold, not yet a booking. */
    @CircuitBreaker(name = "flixbus", fallbackMethod = "checkoutFallback")
    String checkout(Provider provider, String cartId, ContactDetail contact, List<String> ticketIds,
                    List<PassengerDetail> passengers) {
        try {
            FlixBusMapper.CheckoutResponseDto response = restClient.post()
                    .uri(CHECKOUT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .body(mapper.toCheckoutRequest(cartId, contact, ticketIds, passengers,
                            properties.defaultCallingCode()))
                    .retrieve()
                    .body(FlixBusMapper.CheckoutResponseDto.class);

            return mapper.toCheckoutId(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateConfirm(e);
        }
    }

    /** Endpoint 8. One read — the polling loop lives in {@link FlixBusBookingOrchestrator}. */
    @CircuitBreaker(name = "flixbus", fallbackMethod = "getCheckoutFallback")
    FlixBusMapper.CheckoutDetails getCheckout(Provider provider, String checkoutId) {
        try {
            FlixBusMapper.CheckoutDetailsResponseDto response = restClient.get()
                    .uri(CHECKOUT_BY_ID_PATH, checkoutId)
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .retrieve()
                    .body(FlixBusMapper.CheckoutDetailsResponseDto.class);

            return mapper.toCheckoutDetails(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateConfirm(e);
        }
    }

    @SuppressWarnings("unused")
    private String checkoutFallback(Provider provider, String cartId, ContactDetail contact, List<String> ticketIds,
                                    List<PassengerDetail> passengers, Throwable t) {
        throw exceptionTranslator.translateFallback("checkout", t);
    }

    @SuppressWarnings("unused")
    private FlixBusMapper.CheckoutDetails getCheckoutFallback(Provider provider, String checkoutId, Throwable t) {
        throw exceptionTranslator.translateFallback("getCheckout", t);
    }
}
