package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;

import java.time.Duration;
import java.util.Optional;

/** Redis-backed, short-TTL cache of active session tokens, in front of {@link SessionRepository}.
 * A miss or cache failure always falls back to {@link SessionRepository} — this cache is never
 * the source of truth (docs/architecture/high-level-design.md §7: "Redis is always a derived,
 * expendable copy"). */
public interface TokenCache {

    Optional<ProviderToken> get(ProviderSessionId sessionId);

    void put(ProviderSessionId sessionId, ProviderToken token, Duration ttl);

    void evict(ProviderSessionId sessionId);
}
