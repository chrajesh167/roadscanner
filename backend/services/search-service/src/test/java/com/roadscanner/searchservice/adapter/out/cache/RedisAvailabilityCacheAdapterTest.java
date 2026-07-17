package com.roadscanner.searchservice.adapter.out.cache;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.AvailabilityCache;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the availability cache against a real Redis (Testcontainers). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RedisAvailabilityCacheAdapterTest {

    @Autowired
    private AvailabilityCache availabilityCache;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private TripId randomTripId() {
        return new TripId(UUID.randomUUID());
    }

    @Test
    void putAndGetRoundTripAKnownStatus() {
        TripId tripId = randomTripId();

        availabilityCache.put(tripId, AvailabilityStatus.of(12));

        assertThat(availabilityCache.get(tripId)).contains(AvailabilityStatus.of(12));
    }

    @Test
    void getIsEmptyForAnUncachedTrip() {
        assertThat(availabilityCache.get(randomTripId())).isEmpty();
    }

    @Test
    void entryCarriesTheConfiguredTtl() {
        TripId tripId = randomTripId();
        availabilityCache.put(tripId, AvailabilityStatus.of(5));

        Long ttlSeconds = redisTemplate.getExpire("search:availability:" + tripId.value());

        // roadscanner.search.availability.cache-ttl is PT15S in application.yml.
        assertThat(ttlSeconds).isBetween(1L, 15L);
    }
}
