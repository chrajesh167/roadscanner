package com.roadscanner.providerintegrationservice.application.usecase.auth;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.exception.SessionExpiredException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshSession;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Implements {@link RefreshSession}. */
public class RefreshSessionService implements RefreshSession {

    private final SessionRepository sessionRepository;
    private final ProviderConfigurationRepository configurationRepository;
    private final ProviderClientRegistry registry;
    private final TokenCache tokenCache;
    private final Clock clock;

    public RefreshSessionService(SessionRepository sessionRepository, ProviderConfigurationRepository configurationRepository,
                                  ProviderClientRegistry registry, TokenCache tokenCache, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.configurationRepository = configurationRepository;
        this.registry = registry;
        this.tokenCache = tokenCache;
        this.clock = clock;
    }

    @Override
    public Result refresh(Command command) {
        ProviderSession session = sessionRepository.findById(command.sessionId())
                .orElseThrow(() -> new SessionExpiredException(command.sessionId()));
        if (!session.isActive()) {
            throw new SessionExpiredException(command.sessionId());
        }

        Provider provider = configurationRepository.findByType(session.providerType())
                .orElseThrow(() -> new ProviderNotSupportedException(session.providerType()));
        ProviderClient client = registry.resolve(session.providerType());
        ProviderToken newToken = client.refreshSession(provider, session);

        Instant now = clock.instant();
        session.applyRefreshedToken(newToken, now);
        sessionRepository.save(session);

        Duration ttl = Duration.between(now, newToken.expiresAt());
        if (!ttl.isNegative()) {
            tokenCache.put(session.id(), newToken, ttl);
        }

        return new Result(session.id(), newToken.expiresAt());
    }
}
