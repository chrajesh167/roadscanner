package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderAuthenticationException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCredentialsRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves FlixBus's partner credentials from the encrypted store.
 *
 * <p>The single place this adapter obtains a secret. Credentials come from
 * {@code provider_credentials} and nowhere else — never from {@code FlixBusProperties}, never from
 * an environment variable read here. That matters beyond tidiness: an operator rotates a partner
 * secret through the admin API, and a config-sourced copy would keep authenticating with the old
 * value until someone remembered to redeploy.
 *
 * <p>Decryption is not performed here and must not be. {@code EncryptedCredentialConverter} runs at
 * the persistence boundary, so what the repository returns is already plaintext. Decrypting again
 * here would duplicate the cipher's usage and give a second place for a KMS migration to have to
 * touch — the whole reason that logic lives on the column.
 *
 * <p><strong>Header, not session.</strong> {@code X-API-Authentication} is the static partner token
 * required on every request. {@code X-API-Session} — obtained by partner login — is a separate
 * concern for authenticated operations and is Sprint 4's work; nothing here establishes a session.
 */
@Component
class FlixBusCredentials {

    /** The header FlixBus expects the static partner token in, on every request. */
    static final String AUTHENTICATION_HEADER = "X-API-Authentication";

    /**
     * The header carrying the login-derived session token, required by the cart, checkout, payment,
     * order and cancellation calls. Distinct from {@link #AUTHENTICATION_HEADER}: the partner token
     * identifies RoadScanner, the session token authorises acting on a booking.
     */
    static final String SESSION_HEADER = "X-API-Session";

    private final ProviderCredentialsRepository credentialsRepository;

    FlixBusCredentials(ProviderCredentialsRepository credentialsRepository) {
        this.credentialsRepository = credentialsRepository;
    }

    /**
     * The decrypted static partner token for {@code X-API-Authentication}.
     *
     * @throws ProviderAuthenticationException when no credentials are stored, or none carry a
     *         token. Failing here is deliberate: calling FlixBus without the header produces a
     *         rejection that reads like bad credentials, sending whoever debugs it to the provider
     *         instead of to the empty row that actually caused it.
     */
    String partnerToken(Provider provider) {
        ProviderCredentials credentials = credentialsRepository.findByProvider(provider.id())
                .orElseThrow(() -> missing(provider.type(),
                        "No credentials are stored for this provider — store them via "
                                + "PUT /api/v1/providers/{id}/credentials"));

        return credentials.partnerToken()
                .orElseThrow(() -> missing(provider.type(),
                        "Stored credentials carry no partner token, which FlixBus requires on every request"));
    }

    /**
     * The partner account email and password, for a future partner login.
     *
     * <p>Resolved from the same store rather than from configuration, so that when Sprint 4 adds
     * {@code POST /public/v1/partner/authenticate.json} it has nothing left to wire — no secret
     * reaches this adapter by any other route.
     */
    PartnerLogin partnerLogin(Provider provider) {
        ProviderCredentials credentials = credentialsRepository.findByProvider(provider.id())
                .orElseThrow(() -> missing(provider.type(), "No credentials are stored for this provider"));

        String email = credentials.partnerEmail()
                .orElseThrow(() -> missing(provider.type(), "Stored credentials carry no partner email"));
        String password = credentials.partnerPassword()
                .orElseThrow(() -> missing(provider.type(), "Stored credentials carry no partner password"));

        return new PartnerLogin(email, password);
    }

    /** Messages name the missing field, never a value — this method only ever handles secrets. */
    private static ProviderAuthenticationException missing(ProviderType providerType, String detail) {
        return new ProviderAuthenticationException(detail,
                new ProviderError(providerType, "PROVIDER_CREDENTIALS_MISSING",
                        "Provider credentials are not configured", false));
    }

    /** Carries a partner login pair. Renders no secret, for the same reason the aggregate does not. */
    record PartnerLogin(String email, String password) {
        @Override
        public String toString() {
            return "PartnerLogin[email=set, password=set]";
        }
    }
}
