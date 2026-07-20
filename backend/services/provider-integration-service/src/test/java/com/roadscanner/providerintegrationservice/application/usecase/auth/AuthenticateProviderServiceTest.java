package com.roadscanner.providerintegrationservice.application.usecase.auth;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;
import com.roadscanner.providerintegrationservice.testsupport.MutableClock;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemorySessionRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryTokenCache;
import com.roadscanner.providerintegrationservice.testsupport.fakes.StubProviderClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateProviderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryProviderConfigurationRepository configurationRepository = new InMemoryProviderConfigurationRepository();
    private final InMemorySessionRepository sessionRepository = new InMemorySessionRepository();
    private final InMemoryTokenCache tokenCache = new InMemoryTokenCache();

    @Test
    void authenticatesAgainstAnEnabledProviderAndPersistsTheSession() {
        StubProviderClient client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEARCH));
        ProviderToken token = new ProviderToken("access", "refresh", "Bearer", NOW.plusSeconds(3600));
        client.authenticateResult = () -> token;
        configurationRepository.add(Provider.reconstitute(ProviderId.generate(), ProviderType.MOCK, "Mock", true,
                Set.of(ProviderCapability.SEARCH), null, NOW, NOW));

        AuthenticateProvider service = new AuthenticateProviderService(configurationRepository,
                new ProviderClientRegistry(List.of(client)), sessionRepository, tokenCache, clock);

        AuthenticateProvider.Result result = service.authenticate(new AuthenticateProvider.Command(ProviderType.MOCK));

        assertThat(result.providerType()).isEqualTo(ProviderType.MOCK);
        assertThat(result.expiresAt()).isEqualTo(token.expiresAt());
        assertThat(sessionRepository.findById(result.sessionId())).isPresent();
        assertThat(tokenCache.contains(result.sessionId())).isTrue();
    }

    @Test
    void rejectsADisabledProvider() {
        configurationRepository.add(Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS, "FlixBus", false,
                Set.of(ProviderCapability.SEARCH), null, NOW, NOW));
        AuthenticateProvider service = new AuthenticateProviderService(configurationRepository,
                new ProviderClientRegistry(List.of()), sessionRepository, tokenCache, clock);

        assertThatThrownBy(() -> service.authenticate(new AuthenticateProvider.Command(ProviderType.FLIXBUS)))
                .isInstanceOf(ProviderNotSupportedException.class);
    }

    @Test
    void rejectsAnUnconfiguredProvider() {
        AuthenticateProvider service = new AuthenticateProviderService(configurationRepository,
                new ProviderClientRegistry(List.of()), sessionRepository, tokenCache, clock);

        assertThatThrownBy(() -> service.authenticate(new AuthenticateProvider.Command(ProviderType.FLIXBUS)))
                .isInstanceOf(ProviderNotSupportedException.class);
    }
}
