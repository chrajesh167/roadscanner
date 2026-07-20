package com.roadscanner.providerintegrationservice.adapter.out.cache;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises {@link RedisTokenCacheAdapter} against a real Redis (Testcontainers). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RedisTokenCacheAdapterTest {

    @Autowired
    private TokenCache tokenCache;

    @Test
    void putAndGetRoundTripAToken() {
        ProviderSessionId sessionId = ProviderSessionId.generate();
        ProviderToken token = new ProviderToken("access", "refresh", "Bearer", Instant.parse("2026-08-01T00:00:00Z"));

        tokenCache.put(sessionId, token, Duration.ofMinutes(5));

        assertThat(tokenCache.get(sessionId)).contains(token);
    }

    @Test
    void getIsEmptyForAnUncachedSession() {
        assertThat(tokenCache.get(ProviderSessionId.generate())).isEmpty();
    }

    @Test
    void evictRemovesTheEntry() {
        ProviderSessionId sessionId = ProviderSessionId.generate();
        tokenCache.put(sessionId, new ProviderToken("access", null, "Bearer", Instant.parse("2026-08-01T00:00:00Z")),
                Duration.ofMinutes(5));

        tokenCache.evict(sessionId);

        assertThat(tokenCache.get(sessionId)).isEmpty();
    }

    @Test
    void nonPositiveTtlIsNeverCached() {
        ProviderSessionId sessionId = ProviderSessionId.generate();

        tokenCache.put(sessionId, new ProviderToken("access", null, "Bearer", Instant.parse("2026-08-01T00:00:00Z")),
                Duration.ZERO);

        assertThat(tokenCache.get(sessionId)).isEmpty();
    }
}
