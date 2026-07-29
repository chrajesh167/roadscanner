package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;

import java.util.Objects;
import java.util.Optional;

/**
 * Writes the partner credentials this service authenticates to a provider with.
 *
 * <p>Write and existence-check only, on purpose. There is no "get the credentials" operation on
 * this port, because there is no legitimate caller for one outside the authentication path — and
 * an operation that does not exist cannot be accidentally exposed by a future controller. The
 * authentication flow reads them through {@code ProviderCredentialsRepository} directly.
 */
public interface ManageProviderCredentials {

    /** Creates or replaces the provider's credentials wholesale. */
    ProviderCredentials store(StoreCredentialsCommand command);

    /**
     * Whether credentials exist, and when they last changed — never the secrets themselves. This
     * is everything the admin console legitimately needs to render.
     */
    Optional<CredentialsSummary> summarise(ProviderId providerId);

    record StoreCredentialsCommand(ProviderId providerId, String partnerEmail, String partnerPassword,
                                   String partnerToken) {
        public StoreCredentialsCommand {
            Objects.requireNonNull(providerId, "providerId must not be null");
        }
    }

    /** @param encrypted whether this row's secrets are encrypted at rest. Always false in Sprint 2
     *                   — see {@code ProviderCredentials}'s Javadoc. */
    record CredentialsSummary(boolean hasPassword, boolean hasToken, boolean encrypted,
                              java.time.Instant updatedAt) {
    }
}
