package com.roadscanner.providerintegrationservice.application.usecase.auth;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Implements {@link AuthenticateProvider}. A provider must be both configured
 * ({@code ProviderConfigurationRepository}) and {@code enabled} — see the {@code FLIXBUS} seed
 * row, which exists but is disabled until real credentials are configured — to be authenticated
 * against; either gap surfaces as the same {@link ProviderNotSupportedException} a caller would
 * see for a provider with no adapter at all. */
public class AuthenticateProviderService implements AuthenticateProvider {

    private final ProviderConfigurationRepository configurationRepository;
    private final ProviderClientRegistry registry;
    private final SessionRepository sessionRepository;
    private final TokenCache tokenCache;
    private final Clock clock;

    public AuthenticateProviderService(ProviderConfigurationRepository configurationRepository,
                                        ProviderClientRegistry registry, SessionRepository sessionRepository,
                                        TokenCache tokenCache, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.registry = registry;
        this.sessionRepository = sessionRepository;
        this.tokenCache = tokenCache;
        this.clock = clock;
    }

    @Override
    public Result authenticate(Command command) {
        Provider provider = configurationRepository.findByType(command.providerType())
                .filter(Provider::enabled)
                .orElseThrow(() -> new ProviderNotSupportedException(command.providerType()));

        ProviderClient client = registry.resolve(command.providerType());
        ProviderToken token = client.authenticate(provider);

        Instant now = clock.instant();
        ProviderSessionId sessionId = ProviderSessionId.generate();
        ProviderSession session = ProviderSession.open(sessionId, command.providerType(), token, now);
        sessionRepository.save(session);
        cacheToken(sessionId, token, now);

        return new Result(sessionId, command.providerType(), token.expiresAt());
    }

    private void cacheToken(ProviderSessionId sessionId, ProviderToken token, Instant now) {
        Duration ttl = Duration.between(now, token.expiresAt());
        if (!ttl.isNegative()) {
            tokenCache.put(sessionId, token, ttl);
        }
    }
}
