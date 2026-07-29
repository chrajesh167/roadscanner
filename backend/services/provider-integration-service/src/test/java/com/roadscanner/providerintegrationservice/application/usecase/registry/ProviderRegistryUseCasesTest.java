package com.roadscanner.providerintegrationservice.application.usecase.registry;

import com.roadscanner.providerintegrationservice.domain.exception.DuplicateProviderException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviders;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.TestProviderConnection;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Application-layer behaviour against in-memory repositories — the orchestration rules
 * (duplicate pre-check, not-found translation, idempotent toggles, credential rotation) without a
 * Spring context or a database.
 */
class ProviderRegistryUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private InMemoryProviderConfigurationRepository providers;
    private InMemoryProviderCredentialsRepository credentials;
    private ManageProviders manageProviders;

    @BeforeEach
    void setUp() {
        providers = new InMemoryProviderConfigurationRepository();
        credentials = new InMemoryProviderCredentialsRepository();
        manageProviders = new ManageProvidersService(providers, clock);
    }

    private Provider seedFlixbus(boolean enabled) {
        Provider provider = Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS, ProviderCategory.BUS,
                "FlixBus", enabled, Set.of(ProviderCapability.SEARCH), "https://partner.flixbus.com",
                8_000, 2, NOW, NOW);
        providers.add(provider);
        return provider;
    }

    private static ManageProviders.RegisterProviderCommand registerCommand(ProviderType type) {
        return new ManageProviders.RegisterProviderCommand(type, ProviderCategory.BUS, "FlixBus",
                Set.of(ProviderCapability.SEARCH), "https://partner.flixbus.com", 8_000, 2);
    }

    @Nested
    class Registration {

        @Test
        void registersADisabledProvider() {
            Provider registered = manageProviders.register(registerCommand(ProviderType.FLIXBUS));

            assertThat(registered.enabled()).isFalse();
            assertThat(registered.type()).isEqualTo(ProviderType.FLIXBUS);
            assertThat(registered.createdAt()).isEqualTo(NOW);
            assertThat(providers.count()).isEqualTo(1);
        }

        @Test
        void rejectsASecondProviderWithTheSameCode() {
            manageProviders.register(registerCommand(ProviderType.FLIXBUS));

            assertThatThrownBy(() -> manageProviders.register(registerCommand(ProviderType.FLIXBUS)))
                    .isInstanceOf(DuplicateProviderException.class);

            assertThat(providers.count()).isEqualTo(1);
        }

        @Test
        void allowsDistinctProviders() {
            manageProviders.register(registerCommand(ProviderType.FLIXBUS));
            manageProviders.register(registerCommand(ProviderType.MOCK));

            assertThat(providers.count()).isEqualTo(2);
        }
    }

    @Nested
    class Reads {

        @Test
        void listsEveryProviderOrOnlyEnabledOnes() {
            seedFlixbus(false);
            Provider enabled = Provider.reconstitute(ProviderId.generate(), ProviderType.MOCK, ProviderCategory.BUS,
                    "Mock", true, Set.of(), null, 5_000, 1, NOW, NOW);
            providers.add(enabled);

            assertThat(manageProviders.list(false)).hasSize(2);
            assertThat(manageProviders.list(true)).extracting(Provider::type).containsExactly(ProviderType.MOCK);
        }

        @Test
        void getsAProviderById() {
            Provider seeded = seedFlixbus(true);

            assertThat(manageProviders.get(seeded.id()).type()).isEqualTo(ProviderType.FLIXBUS);
        }

        @Test
        void throwsWhenTheIdIsUnknown() {
            assertThatThrownBy(() -> manageProviders.get(ProviderId.generate()))
                    .isInstanceOf(ProviderNotFoundException.class);
        }
    }

    @Nested
    class Updates {

        @Test
        void replacesEditableFieldsAndStampsUpdatedAt() {
            Provider seeded = seedFlixbus(true);

            Provider updated = manageProviders.update(new ManageProviders.UpdateProviderCommand(
                    seeded.id(), ProviderCategory.RAIL, "FlixTrain", Set.of(ProviderCapability.SEAT_MAP),
                    "https://rail.example.com", 3_000, 1));

            assertThat(updated.displayName()).isEqualTo("FlixTrain");
            assertThat(updated.category()).isEqualTo(ProviderCategory.RAIL);
            assertThat(updated.timeoutMillis()).isEqualTo(3_000);
            assertThat(updated.updatedAt()).isEqualTo(NOW);
            // An edit never silently takes a provider out of service.
            assertThat(updated.enabled()).isTrue();
        }

        @Test
        void throwsWhenTheIdIsUnknown() {
            assertThatThrownBy(() -> manageProviders.update(new ManageProviders.UpdateProviderCommand(
                    ProviderId.generate(), ProviderCategory.BUS, "X", Set.of(), null, 5_000, 2)))
                    .isInstanceOf(ProviderNotFoundException.class);
        }
    }

    @Nested
    class Toggles {

        @Test
        void enableReportsWhetherItChangedAnything() {
            Provider seeded = seedFlixbus(false);

            assertThat(manageProviders.enable(seeded.id()).changed()).isTrue();
            assertThat(manageProviders.enable(seeded.id()).changed()).isFalse();
            assertThat(manageProviders.get(seeded.id()).enabled()).isTrue();
        }

        @Test
        void disableReportsWhetherItChangedAnything() {
            Provider seeded = seedFlixbus(true);

            assertThat(manageProviders.disable(seeded.id()).changed()).isTrue();
            assertThat(manageProviders.disable(seeded.id()).changed()).isFalse();
            assertThat(manageProviders.get(seeded.id()).enabled()).isFalse();
        }

        @Test
        void disableIsNotADelete() {
            Provider seeded = seedFlixbus(true);

            manageProviders.disable(seeded.id());

            // Sessions, health records and audit rows reference this provider.
            assertThat(providers.findById(seeded.id())).isPresent();
        }

        @Test
        void throwsWhenTheIdIsUnknown() {
            assertThatThrownBy(() -> manageProviders.enable(ProviderId.generate()))
                    .isInstanceOf(ProviderNotFoundException.class);
            assertThatThrownBy(() -> manageProviders.disable(ProviderId.generate()))
                    .isInstanceOf(ProviderNotFoundException.class);
        }
    }

    @Nested
    class Credentials {

        private ManageProviderCredentials useCase;

        @BeforeEach
        void setUpCredentials() {
            useCase = new ManageProviderCredentialsService(credentials, providers, clock);
        }

        @Test
        void storesCredentialsForAKnownProvider() {
            Provider seeded = seedFlixbus(false);

            useCase.store(new ManageProviderCredentials.StoreCredentialsCommand(
                    seeded.id(), "partner@roadscanner.com", "s3cret", null));

            assertThat(credentials.count()).isEqualTo(1);
            assertThat(useCase.summarise(seeded.id()).orElseThrow().hasPassword()).isTrue();
        }

        @Test
        void rotatesRatherThanDuplicatingOnASecondStore() {
            Provider seeded = seedFlixbus(false);
            useCase.store(new ManageProviderCredentials.StoreCredentialsCommand(
                    seeded.id(), "partner@roadscanner.com", "old", null));

            useCase.store(new ManageProviderCredentials.StoreCredentialsCommand(
                    seeded.id(), "partner@roadscanner.com", null, "token-new"));

            // V6 allows one credential set per provider — a second store must replace, not collide.
            assertThat(credentials.count()).isEqualTo(1);
            var summary = useCase.summarise(seeded.id()).orElseThrow();
            assertThat(summary.hasToken()).isTrue();
            assertThat(summary.hasPassword()).isFalse();
        }

        @Test
        void refusesToStoreCredentialsForAnUnknownProvider() {
            assertThatThrownBy(() -> useCase.store(new ManageProviderCredentials.StoreCredentialsCommand(
                    ProviderId.generate(), "partner@roadscanner.com", "s3cret", null)))
                    .isInstanceOf(ProviderNotFoundException.class);

            // Never leave a credential row orphaned with real secrets in it.
            assertThat(credentials.count()).isZero();
        }

        @Test
        void refusesCredentialsThatCannotAuthenticateAnything() {
            Provider seeded = seedFlixbus(false);

            assertThatThrownBy(() -> useCase.store(new ManageProviderCredentials.StoreCredentialsCommand(
                    seeded.id(), "partner@roadscanner.com", null, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void summariseIsEmptyWhenNoCredentialsAreStored() {
            Provider seeded = seedFlixbus(false);

            assertThat(useCase.summarise(seeded.id())).isEmpty();
        }

        @Test
        void summariseRejectsAnUnknownProvider() {
            assertThatThrownBy(() -> useCase.summarise(ProviderId.generate()))
                    .isInstanceOf(ProviderNotFoundException.class);
        }
    }

    @Nested
    class Delegation {

        @Test
        void testConnectionDelegatesToTheSharedHealthCheck() {
            Provider seeded = seedFlixbus(true);
            List<ProviderType> probed = new ArrayList<>();
            CheckProviderHealth health = command -> {
                probed.add(command.providerType());
                return new CheckProviderHealth.Result(ProviderHealth.unknown(command.providerType(), NOW));
            };

            TestProviderConnection useCase = new TestProviderConnectionService(providers, health);
            TestProviderConnection.Result result = useCase.test(new TestProviderConnection.Command(seeded.id()));

            // The admin test must be the same probe the platform runs, not a lookalike.
            assertThat(probed).containsExactly(ProviderType.FLIXBUS);
            assertThat(result.health().currentState()).isEqualTo(HealthState.UNKNOWN);
        }

        @Test
        void testConnectionRejectsAnUnknownProvider() {
            TestProviderConnection useCase = new TestProviderConnectionService(providers,
                    command -> new CheckProviderHealth.Result(ProviderHealth.unknown(command.providerType(), NOW)));

            assertThatThrownBy(() -> useCase.test(new TestProviderConnection.Command(ProviderId.generate())))
                    .isInstanceOf(ProviderNotFoundException.class);
        }

        @Test
        void refreshSessionDelegatesToTheSharedAuthentication() {
            Provider seeded = seedFlixbus(true);
            ProviderSessionId sessionId = ProviderSessionId.generate();
            List<ProviderType> authenticated = new ArrayList<>();
            AuthenticateProvider auth = command -> {
                authenticated.add(command.providerType());
                return new AuthenticateProvider.Result(sessionId, command.providerType(), NOW.plusSeconds(3600));
            };

            RefreshProviderSession useCase = new RefreshProviderSessionService(providers, auth);
            RefreshProviderSession.Result result = useCase.refresh(new RefreshProviderSession.Command(seeded.id()));

            assertThat(authenticated).containsExactly(ProviderType.FLIXBUS);
            assertThat(result.sessionId()).isEqualTo(sessionId);
            assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        }

        @Test
        void refreshSessionRejectsAnUnknownProvider() {
            RefreshProviderSession useCase = new RefreshProviderSessionService(providers,
                    command -> new AuthenticateProvider.Result(ProviderSessionId.generate(), command.providerType(),
                            NOW));

            assertThatThrownBy(() -> useCase.refresh(new RefreshProviderSession.Command(ProviderId.generate())))
                    .isInstanceOf(ProviderNotFoundException.class);
        }
    }
}
