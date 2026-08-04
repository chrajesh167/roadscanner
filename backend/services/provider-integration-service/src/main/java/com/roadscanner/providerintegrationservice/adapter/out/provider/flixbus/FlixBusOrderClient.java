package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Endpoints 10 and 11 — {@code GET /order/v3/orders/{orderId}} and
 * {@code PUT /order/v3/orders/{orderId}/partner/cancel}.
 *
 * <p>Both are authorised by the order token issued at booking, passed as a query parameter, in
 * addition to the two headers. An order reference on its own is not sufficient to read or cancel —
 * which is why {@code BookingConfirmation} keeps the token.
 *
 * <p>As with the checkout read, the reference implementation omits the auth headers here and the
 * documentation says to send them anyway when building fresh. They are sent.
 */
@Component
class FlixBusOrderClient {

    static final String ORDER_PATH = "/order/v3/orders/{orderId}";
    static final String CANCEL_PATH = "/order/v3/orders/{orderId}/partner/cancel";

    /** The initial order version. Increments as the order mutates, e.g. after a cancellation. */
    private static final int INITIAL_VERSION = 1;

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusRequestContext context;

    FlixBusOrderClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                       FlixBusExceptionTranslator exceptionTranslator, FlixBusRequestContext context) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.context = context;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "getOrderFallback")
    ProviderOrder getOrder(Provider provider, String orderId, String orderToken) {
        try {
            java.util.Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(ORDER_PATH)
                            .queryParam("token", orderToken)
                            .queryParam("version", INITIAL_VERSION)
                            .build(orderId))
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {
                    });

            return mapper.toProviderOrder(orderId, body);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateConfirm(e);
        }
    }

    /**
     * Cancels the whole order. An empty {@code items} array is the documented way to say "all of
     * it" — a partial cancellation would name the items, which this integration does not do.
     *
     * <p>Single attempt: a timed-out cancellation may already have refunded.
     */
    @CircuitBreaker(name = "flixbus", fallbackMethod = "cancelOrderFallback")
    CancellationResult cancelOrder(Provider provider, String orderId, String orderToken, String reason) {
        try {
            FlixBusMapper.CancellationResponseDto response = restClient.put()
                    .uri(uriBuilder -> uriBuilder.path(CANCEL_PATH).queryParam("token", orderToken).build(orderId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .body(mapper.toCancellationRequest(reason))
                    .retrieve()
                    .body(FlixBusMapper.CancellationResponseDto.class);

            return mapper.toCancellationResult(orderId, response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateConfirm(e);
        }
    }

    @SuppressWarnings("unused")
    private ProviderOrder getOrderFallback(Provider provider, String orderId, String orderToken, Throwable t) {
        throw exceptionTranslator.translateFallback("getOrderDetails", t);
    }

    @SuppressWarnings("unused")
    private CancellationResult cancelOrderFallback(Provider provider, String orderId, String orderToken,
                                                   String reason, Throwable t) {
        throw exceptionTranslator.translateFallback("cancelBooking", t);
    }
}
