package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryTokenCache implements TokenCache {

    private final Map<ProviderSessionId, ProviderToken> tokens = new LinkedHashMap<>();

    @Override
    public Optional<ProviderToken> get(ProviderSessionId sessionId) {
        return Optional.ofNullable(tokens.get(sessionId));
    }

    @Override
    public void put(ProviderSessionId sessionId, ProviderToken token, Duration ttl) {
        tokens.put(sessionId, token);
    }

    @Override
    public void evict(ProviderSessionId sessionId) {
        tokens.remove(sessionId);
    }

    public boolean contains(ProviderSessionId sessionId) {
        return tokens.containsKey(sessionId);
    }
}
