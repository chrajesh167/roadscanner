package com.roadscanner.providerintegrationservice.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The registry aggregates' own invariants — no Spring, no database. */
class ProviderRegistryModelTest {

    private static final Instant CREATED = Instant.parse("2026-07-29T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-30T00:00:00Z");

    private static Provider flixbus() {
        return Provider.register(ProviderId.generate(), ProviderType.FLIXBUS, ProviderCategory.BUS, "FlixBus",
                Set.of(ProviderCapability.SEARCH), "https://partner.flixbus.com", 8_000, 2, CREATED);
    }

    @Nested
    class Registration {

        @Test
        void alwaysStartsDisabled() {
            // Registering a row does not make a provider usable — an adapter must exist too, and
            // that cannot be verified at the moment someone POSTs. Enabling is a separate act.
            assertThat(flixbus().enabled()).isFalse();
        }

        @Test
        void stampsBothTimestampsWithTheSameInstant() {
            Provider provider = flixbus();

            assertThat(provider.createdAt()).isEqualTo(CREATED);
            assertThat(provider.updatedAt()).isEqualTo(CREATED);
        }

        @Test
        void rejectsABlankDisplayName() {
            assertThatThrownBy(() -> Provider.register(ProviderId.generate(), ProviderType.FLIXBUS,
                    ProviderCategory.BUS, "  ", Set.of(), null, 5_000, 2, CREATED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("displayName");
        }

        @Test
        void rejectsANonPositiveTimeout() {
            assertThatThrownBy(() -> Provider.register(ProviderId.generate(), ProviderType.FLIXBUS,
                    ProviderCategory.BUS, "FlixBus", Set.of(), null, 0, 2, CREATED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeoutMillis");
        }

        @Test
        void rejectsAnUnboundedRetryCount() {
            // Retrying forever turns one slow provider into an outage for everything sharing its
            // thread pool.
            assertThatThrownBy(() -> Provider.register(ProviderId.generate(), ProviderType.FLIXBUS,
                    ProviderCategory.BUS, "FlixBus", Set.of(), null, 5_000, Provider.MAX_RETRY_COUNT + 1, CREATED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("retryCount");

            assertThatThrownBy(() -> Provider.register(ProviderId.generate(), ProviderType.FLIXBUS,
                    ProviderCategory.BUS, "FlixBus", Set.of(), null, 5_000, -1, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void enableAndDisableAreIdempotent() {
            Provider provider = flixbus();

            assertThat(provider.enable(LATER)).isTrue();
            assertThat(provider.enable(LATER)).isFalse();
            assertThat(provider.enabled()).isTrue();

            assertThat(provider.disable(LATER)).isTrue();
            assertThat(provider.disable(LATER)).isFalse();
            assertThat(provider.enabled()).isFalse();
        }

        @Test
        void aNoOpToggleDoesNotBumpUpdatedAt() {
            Provider provider = flixbus();
            provider.enable(LATER);

            provider.enable(Instant.parse("2026-08-01T00:00:00Z"));

            // The row is @Version-mapped; a no-op that bumped the timestamp would also burn an
            // optimistic-locking version for nothing.
            assertThat(provider.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void updateReplacesEditableFieldsWithoutTouchingIdentityOrEnabledState() {
            Provider provider = flixbus();
            provider.enable(CREATED);

            provider.update(ProviderCategory.RAIL, "FlixTrain", Set.of(ProviderCapability.SEAT_MAP),
                    "https://rail.example.com", 3_000, 0, LATER);

            assertThat(provider.category()).isEqualTo(ProviderCategory.RAIL);
            assertThat(provider.displayName()).isEqualTo("FlixTrain");
            assertThat(provider.capabilities()).containsExactly(ProviderCapability.SEAT_MAP);
            assertThat(provider.timeoutMillis()).isEqualTo(3_000);
            assertThat(provider.retryCount()).isZero();
            assertThat(provider.updatedAt()).isEqualTo(LATER);
            // Identity is untouched — sessions and health rows are keyed on it.
            assertThat(provider.type()).isEqualTo(ProviderType.FLIXBUS);
            // Enabling has its own operation and is never an incidental side effect of a rename.
            assertThat(provider.enabled()).isTrue();
        }

        @Test
        void identityIsTheIdAlone() {
            ProviderId id = ProviderId.generate();
            Provider one = Provider.reconstitute(id, ProviderType.FLIXBUS, ProviderCategory.BUS, "A", true,
                    Set.of(), null, 5_000, 2, CREATED, CREATED);
            Provider other = Provider.reconstitute(id, ProviderType.MOCK, ProviderCategory.RAIL, "B", false,
                    Set.of(), null, 1_000, 0, LATER, LATER);

            assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
            assertThat(one).isNotEqualTo(flixbus()).isNotEqualTo("not a provider");
        }

        @Test
        void neverRendersMoreThanTypeAndState() {
            assertThat(flixbus()).hasToString("Provider[FLIXBUS disabled]");
        }
    }

    @Nested
    class Category {

        @Test
        void normalisesCaseSoOneVerticalCannotBecomeTwo() {
            assertThat(new ProviderCategory("bus")).isEqualTo(ProviderCategory.BUS);
            assertThat(new ProviderCategory(" Rail ")).isEqualTo(ProviderCategory.RAIL);
        }

        @Test
        void rejectsBlankAndOverlongCodes() {
            assertThatThrownBy(() -> new ProviderCategory("  ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ProviderCategory(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ProviderCategory("x".repeat(51)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rendersItsCode() {
            assertThat(ProviderCategory.AIRLINE).hasToString("AIRLINE");
        }
    }

    @Nested
    class Credentials {

        private static ProviderCredentials withPassword() {
            return ProviderCredentials.issue(ProviderCredentialsId.generate(), ProviderId.generate(),
                    "partner@roadscanner.com", "s3cret", null, CREATED);
        }

        @Test
        void acceptsEitherAPasswordOrAToken() {
            assertThat(withPassword().hasPassword()).isTrue();

            ProviderCredentials tokenOnly = ProviderCredentials.issue(ProviderCredentialsId.generate(),
                    ProviderId.generate(), null, null, "token-abc", CREATED);
            assertThat(tokenOnly.hasToken()).isTrue();
            assertThat(tokenOnly.hasPassword()).isFalse();
        }

        @Test
        void rejectsCredentialsThatCannotAuthenticateAnything() {
            assertThatThrownBy(() -> ProviderCredentials.issue(ProviderCredentialsId.generate(),
                    ProviderId.generate(), "partner@roadscanner.com", null, null, CREATED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least");
        }

        @Test
        void normalisesBlankSecretsToAbsent() {
            ProviderCredentials credentials = ProviderCredentials.issue(ProviderCredentialsId.generate(),
                    ProviderId.generate(), "  ", "   ", "token-abc", CREATED);

            assertThat(credentials.partnerEmail()).isEmpty();
            assertThat(credentials.hasPassword()).isFalse();
        }

        @Test
        void rotateReplacesSecretsWholesale() {
            ProviderCredentials credentials = withPassword();

            credentials.rotate(null, null, "token-new", LATER);

            assertThat(credentials.hasPassword()).isFalse();
            assertThat(credentials.partnerToken()).contains("token-new");
            assertThat(credentials.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void rotateStillRequiresSomethingToAuthenticateWith() {
            ProviderCredentials credentials = withPassword();

            assertThatThrownBy(() -> credentials.rotate("partner@roadscanner.com", null, null, LATER))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void neverRendersASecret() {
            ProviderCredentials credentials = ProviderCredentials.issue(ProviderCredentialsId.generate(),
                    ProviderId.generate(), "partner@roadscanner.com", "s3cret", "token-abc", CREATED);

            // An aggregate that prints its own password ends up in a log line eventually.
            assertThat(credentials.toString())
                    .doesNotContain("s3cret", "token-abc", "partner@roadscanner.com")
                    .contains("password=set", "token=set");
        }

        @Test
        void exposesItsIdentityAndTimestamps() {
            ProviderCredentialsId id = ProviderCredentialsId.generate();
            ProviderId providerId = ProviderId.generate();

            ProviderCredentials credentials = ProviderCredentials.issue(id, providerId, null, "s3cret", null, CREATED);

            assertThat(credentials.id()).isEqualTo(id);
            assertThat(credentials.providerId()).isEqualTo(providerId);
            assertThat(credentials.createdAt()).isEqualTo(CREATED);
            assertThat(credentials.updatedAt()).isEqualTo(CREATED);
        }

        @Test
        void identityIsTheIdAlone() {
            ProviderCredentialsId id = ProviderCredentialsId.generate();
            ProviderCredentials one = ProviderCredentials.reconstitute(id, ProviderId.generate(), null, "a", null,
                    false, CREATED, CREATED);
            ProviderCredentials other = ProviderCredentials.reconstitute(id, ProviderId.generate(), null, "b", null,
                    true, LATER, LATER);

            assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
            assertThat(one).isNotEqualTo(withPassword()).isNotEqualTo("not credentials");
        }

        @Test
        void credentialIdsAreMintedUniquelyAndRenderTheirValue() {
            ProviderCredentialsId id = ProviderCredentialsId.generate();

            assertThat(id).isNotEqualTo(ProviderCredentialsId.generate());
            assertThat(id).hasToString(id.value().toString());
            assertThatThrownBy(() -> new ProviderCredentialsId(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void reconstituteRestoresTheEncryptedFlag() {
            ProviderCredentials credentials = ProviderCredentials.reconstitute(ProviderCredentialsId.generate(),
                    ProviderId.generate(), null, "s3cret", null, true, CREATED, LATER);

            // Rehydration trusts stored state — a row already converted must not come back looking
            // like plaintext.
            assertThat(credentials.isEncrypted()).isTrue();
            assertThat(credentials.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void rotatingKeepsTheRowMarkedEncrypted() {
            ProviderCredentials credentials = ProviderCredentials.reconstitute(ProviderCredentialsId.generate(),
                    ProviderId.generate(), null, "old", null, false, CREATED, CREATED);

            credentials.rotate(null, "new-secret", null, LATER);

            // Since Sprint 2.1 every write goes through the encrypting converter, so a rotation
            // leaves the row encrypted regardless of what it was before — which is also how a
            // legacy plaintext row gets converted: by being written again.
            assertThat(credentials.isEncrypted()).isTrue();
        }

        @Test
        void newlyIssuedCredentialsAreMarkedEncrypted() {
            // Sprint 2.1 made this real: EncryptedCredentialConverter encrypts the secret columns
            // on every write, so the flag now records an accomplished fact rather than an intent.
            assertThat(withPassword().isEncrypted()).isTrue();
        }
    }
}
