package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the FlixBus partner session token and re-logs in when it is no longer usable.
 *
 * <p>The API reference is explicit that the session is obtained once and reused — logging in per
 * request would turn every search into two calls and would hammer an endpoint that exists to be
 * called rarely. So the token is cached per provider registration for {@code sessionTtl}.
 *
 * <p>Two distinct triggers replace it, and both are needed. <strong>Expiry</strong> handles the
 * normal case. <strong>Rejection</strong> handles the case expiry cannot: FlixBus may invalidate a
 * session early, and a cache that only trusts its own clock would then keep presenting a dead token
 * until its TTL elapsed — hours of failing bookings. {@link #invalidate(Provider)} lets a caller
 * that saw a rejection force the next call to re-authenticate.
 *
 * <p>In-memory rather than Redis, matching how {@code inventory-service} caches its provider
 * sessions: a lost token on restart costs one extra login, not correctness. Keyed by provider id so
 * two FlixBus registrations never share a session — they may hold different partner credentials.
 */
@Component
class FlixBusSessionProvider {

    private static final Logger log = LoggerFactory.getLogger(FlixBusSessionProvider.class);

    private final FlixBusAuthenticationClient authenticationClient;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicReference<ProviderToken>> tokens = new ConcurrentHashMap<>();

    FlixBusSessionProvider(FlixBusAuthenticationClient authenticationClient, Clock clock) {
        this.authenticationClient = authenticationClient;
        this.clock = clock;
    }

    /** The session token for {@code X-API-Session}, logging in only if there is no usable one. */
    String sessionToken(Provider provider) {
        AtomicReference<ProviderToken> slot = tokens.computeIfAbsent(key(provider), k -> new AtomicReference<>());

        ProviderToken cached = slot.get();
        if (isUsable(cached)) {
            return cached.accessToken();
        }

        // Synchronized on the slot rather than the map so a login for one provider never blocks
        // another's. The re-check inside covers the thread that lost the race: without it, a burst
        // of concurrent first calls would each perform their own redundant login.
        synchronized (slot) {
            ProviderToken current = slot.get();
            if (isUsable(current)) {
                return current.accessToken();
            }
            ProviderToken fresh = authenticationClient.login(provider);
            slot.set(fresh);
            return fresh.accessToken();
        }
    }

    /**
     * Discards the cached token so the next call logs in again.
     *
     * <p>Called when FlixBus rejects a session that this cache still believed was valid — the only
     * signal available that a token died before its expiry.
     */
    void invalidate(Provider provider) {
        AtomicReference<ProviderToken> slot = tokens.get(key(provider));
        if (slot != null && slot.getAndSet(null) != null) {
            log.info("Discarded FlixBus session for provider={} after it was rejected; will re-authenticate",
                    provider.id().value());
        }
    }

    Optional<ProviderToken> cachedToken(Provider provider) {
        return Optional.ofNullable(tokens.get(key(provider))).map(AtomicReference::get);
    }

    private boolean isUsable(ProviderToken token) {
        return token != null && !token.isExpired(clock.instant());
    }

    private static String key(Provider provider) {
        return provider.id().value().toString();
    }
}
