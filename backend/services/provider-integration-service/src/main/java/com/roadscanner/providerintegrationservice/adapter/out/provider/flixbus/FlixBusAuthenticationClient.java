package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;

/**
 * Endpoint 1 — {@code POST /public/v1/partner/authenticate.json}.
 *
 * <p>Form-encoded, not JSON: the documented contract is
 * {@code application/x-www-form-urlencoded} with {@code email} and {@code password}, carrying the
 * static partner token in {@code X-API-Authentication}. The response is
 * {@code {"token": "<session_token>"}} and that token becomes {@code X-API-Session} everywhere else.
 *
 * <p>There is <strong>no refresh endpoint</strong> in the documented API. Renewal is a fresh login,
 * which is why this class exposes only {@code login} — see
 * {@link FlixBusProviderClientAdapter#refreshSession}.
 */
@Component
class FlixBusAuthenticationClient {

    static final String LOGIN_PATH = "/public/v1/partner/authenticate.json";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusCredentials credentials;
    private final FlixBusProperties properties;
    private final Clock clock;

    FlixBusAuthenticationClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                                FlixBusExceptionTranslator exceptionTranslator, FlixBusCredentials credentials,
                                FlixBusProperties properties, Clock clock) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.credentials = credentials;
        this.properties = properties;
        this.clock = clock;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "loginFallback")
    ProviderToken login(Provider provider) {
        FlixBusCredentials.PartnerLogin partnerLogin = credentials.partnerLogin(provider);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", partnerLogin.email());
        form.add("password", partnerLogin.password());

        try {
            FlixBusMapper.PartnerLoginResponseDto response = restClient.post()
                    .uri(LOGIN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(FlixBusCredentials.AUTHENTICATION_HEADER, credentials.partnerToken(provider))
                    .body(form)
                    .retrieve()
                    .body(FlixBusMapper.PartnerLoginResponseDto.class);

            return mapper.toSessionToken(response, clock.instant().plus(properties.sessionTtl()));
        } catch (RestClientException e) {
            throw exceptionTranslator.translateAuthentication(e);
        }
    }

    @SuppressWarnings("unused")
    private ProviderToken loginFallback(Provider provider, Throwable t) {
        throw exceptionTranslator.translateFallback("authenticate", t);
    }

    /**
     * Health probe by partner login.
     *
     * <p>The documented API exposes no health endpoint, and inventing one would mean calling a URL
     * FlixBus never published. Login is the honest substitute: it is documented, cheap, and it
     * exercises exactly what a caller needs to be true — FlixBus is reachable and our stored
     * credentials are still accepted. A probe against a bare ping would report healthy while every
     * booking failed on a revoked partner secret.
     *
     * <p>No resilience annotations: a probe exists to observe current state, so it must never be
     * short-circuited by an open breaker. It degrades to {@code UNAVAILABLE} rather than throwing.
     */
    ProviderHealthCheck checkHealth(Provider provider) {
        long startedAt = System.nanoTime();
        try {
            login(provider);
            return mapper.toHealthyCheck(startedAt);
        } catch (RuntimeException e) {
            return exceptionTranslator.translateHealthCheck(e);
        }
    }
}
