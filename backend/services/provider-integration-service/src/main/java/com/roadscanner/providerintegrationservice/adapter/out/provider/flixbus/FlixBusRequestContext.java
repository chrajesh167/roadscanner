package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Applies FlixBus's two layered authentication headers.
 *
 * <p>The documented API splits authentication in two: {@code X-API-Authentication} carries the
 * static partner token on <em>every</em> call, and {@code X-API-Session} carries a login-derived
 * session token on the cart, checkout, payment and cancellation calls. Getting that split wrong is
 * easy and fails confusingly — a missing session header returns an authentication error that looks
 * like bad partner credentials — so it is applied here once instead of being re-decided per client.
 *
 * <p>Two methods rather than a boolean flag, because a call site should read as which kind of call
 * it is, not as a parameter whose meaning has to be looked up.
 */
@Component
class FlixBusRequestContext {

    private final FlixBusCredentials credentials;
    private final FlixBusSessionProvider sessionProvider;

    FlixBusRequestContext(FlixBusCredentials credentials, FlixBusSessionProvider sessionProvider) {
        this.credentials = credentials;
        this.sessionProvider = sessionProvider;
    }

    /** Partner token only — for search and seat-map, which document no session requirement. */
    void applyPartnerToken(HttpHeaders headers, Provider provider) {
        headers.set(FlixBusCredentials.AUTHENTICATION_HEADER, credentials.partnerToken(provider));
    }

    /** Partner token plus session — for cart, checkout, payment, order and cancellation. */
    void applyAuthenticated(HttpHeaders headers, Provider provider) {
        applyPartnerToken(headers, provider);
        headers.set(FlixBusCredentials.SESSION_HEADER, sessionProvider.sessionToken(provider));
    }
}
