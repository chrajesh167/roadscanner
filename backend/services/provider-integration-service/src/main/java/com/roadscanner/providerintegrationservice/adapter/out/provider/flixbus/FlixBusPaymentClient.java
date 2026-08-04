package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Endpoint 9 — {@code POST /public/v2/payments}.
 *
 * <p>Declares that the fare was settled out of band. RoadScanner collects payment itself and tells
 * FlixBus so; no card or UPI detail passes through FlixBus. The {@code psp}/{@code method} pair
 * expressing that is configuration, not a constant, because a different settlement arrangement in a
 * future market is a commercial change rather than a code change.
 *
 * <p>The response body is not inspected beyond its status: the documented contract defines success
 * by status code, and the order itself only becomes readable on the subsequent checkout poll.
 *
 * <p><strong>Never retried.</strong> A timed-out payment may already have been accepted, and
 * repeating it is how a traveller is charged twice. The execution policy enforces this; the
 * annotation here only guards against a provider that is already known to be failing.
 */
@Component
class FlixBusPaymentClient {

    static final String PAYMENTS_PATH = "/public/v2/payments";

    private final RestClient restClient;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusRequestContext context;
    private final FlixBusProperties properties;

    FlixBusPaymentClient(RestClient flixBusRestClient, FlixBusExceptionTranslator exceptionTranslator,
                         FlixBusRequestContext context, FlixBusProperties properties) {
        this.restClient = flixBusRestClient;
        this.exceptionTranslator = exceptionTranslator;
        this.context = context;
        this.properties = properties;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "payFallback")
    void pay(Provider provider, String checkoutId) {
        try {
            restClient.post()
                    .uri(PAYMENTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> context.applyAuthenticated(headers, provider))
                    .body(new FlixBusMapper.PaymentRequestDto(checkoutId, properties.paymentProvider(),
                            properties.paymentMethod(), ""))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw exceptionTranslator.translateConfirm(e);
        }
    }

    @SuppressWarnings("unused")
    private void payFallback(Provider provider, String checkoutId, Throwable t) {
        throw exceptionTranslator.translateFallback("payment", t);
    }
}
