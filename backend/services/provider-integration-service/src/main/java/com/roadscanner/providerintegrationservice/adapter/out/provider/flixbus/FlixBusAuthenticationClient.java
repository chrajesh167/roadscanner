package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderAuthenticationException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@code POST /oauth/token} and {@code GET /health} — see {@link FlixBusMapper}'s Javadoc for
 * the full documented contract. Resilience4j instance {@code flixbus} (application.yml). */
@Component
class FlixBusAuthenticationClient {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final String HEALTH_PATH = "/v1/health";

    private final RestClient restClient;
    private final FlixBusMapper mapper;
    private final FlixBusExceptionTranslator exceptionTranslator;
    private final FlixBusProperties properties;

    FlixBusAuthenticationClient(RestClient flixBusRestClient, FlixBusMapper mapper,
                                 FlixBusExceptionTranslator exceptionTranslator, FlixBusProperties properties) {
        this.restClient = flixBusRestClient;
        this.mapper = mapper;
        this.exceptionTranslator = exceptionTranslator;
        this.properties = properties;
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "authenticateFallback")
    @Retry(name = "flixbus")
    ProviderToken authenticate(Provider provider) {
        try {
            FlixBusMapper.TokenResponseDto response = restClient.post()
                    .uri(TOKEN_PATH)
                    .body(mapper.toClientCredentialsRequest(properties))
                    .retrieve()
                    .body(FlixBusMapper.TokenResponseDto.class);
            return mapper.toProviderToken(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateAuthentication(e);
        }
    }

    @SuppressWarnings("unused")
    private ProviderToken authenticateFallback(Provider provider, Throwable t) {
        throw exceptionTranslator.translateFallback("authenticate", t);
    }

    @CircuitBreaker(name = "flixbus", fallbackMethod = "refreshFallback")
    @Retry(name = "flixbus")
    ProviderToken refresh(Provider provider, ProviderSession session) {
        try {
            String refreshToken = session.token().refreshTokenIfPresent()
                    .orElseThrow(() -> new ProviderAuthenticationException(
                            "FlixBus session has no refresh token; re-authenticate instead",
                            new ProviderError(ProviderType.FLIXBUS, "NO_REFRESH_TOKEN",
                                    "Session has no refresh token", false)));
            FlixBusMapper.TokenResponseDto response = restClient.post()
                    .uri(TOKEN_PATH)
                    .body(mapper.toRefreshTokenRequest(properties, refreshToken))
                    .retrieve()
                    .body(FlixBusMapper.TokenResponseDto.class);
            return mapper.toProviderToken(response);
        } catch (RestClientException e) {
            throw exceptionTranslator.translateAuthentication(e);
        }
    }

    @SuppressWarnings("unused")
    private ProviderToken refreshFallback(Provider provider, ProviderSession session, Throwable t) {
        throw exceptionTranslator.translateFallback("refreshSession", t);
    }

    /** No resilience annotations — a health probe's whole purpose is to observe the provider's
     * current state, so it must never be short-circuited by an open breaker; it degrades to an
     * {@code UNAVAILABLE} result on any failure instead of throwing (see
     * {@link FlixBusExceptionTranslator#translateHealthCheck}). */
    ProviderHealthCheck checkHealth() {
        try {
            FlixBusMapper.HealthResponseDto response = restClient.get()
                    .uri(HEALTH_PATH)
                    .retrieve()
                    .body(FlixBusMapper.HealthResponseDto.class);
            return mapper.toHealthCheck(response);
        } catch (RestClientException e) {
            return exceptionTranslator.translateHealthCheck(e);
        }
    }
}
