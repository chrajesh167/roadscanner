package com.roadscanner.providerintegrationservice.adapter.out.cache;

import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatStatus;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCache;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises {@link RedisProviderCacheAdapter} against a real Redis (Testcontainers). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RedisProviderCacheAdapterTest {

    @Autowired
    private ProviderCache providerCache;

    @Test
    void putAndGetRoundTripCapabilities() {
        Set<ProviderCapability> capabilities = Set.of(ProviderCapability.SEARCH, ProviderCapability.SEAT_MAP);

        providerCache.putCapabilities(ProviderType.MOCK, capabilities);

        assertThat(providerCache.getCapabilities(ProviderType.MOCK)).contains(capabilities);
    }

    @Test
    void getCapabilitiesIsEmptyWhenUncached() {
        assertThat(providerCache.getCapabilities(new ProviderType("UNCACHED"))).isEmpty();
    }

    @Test
    void putAndGetRoundTripASeatMap() {
        ProviderSeat seat = new ProviderSeat(new SeatNumber("L1"), "LOWER", "AC Sleeper", SeatStatus.AVAILABLE,
                new FareAmount(BigDecimal.valueOf(500), Currency.getInstance("INR")));
        ProviderSeatMap seatMap = new ProviderSeatMap("TRIP-1", ProviderType.MOCK, List.of(seat));

        providerCache.putSeatMap(ProviderType.MOCK, "TRIP-1", seatMap);

        assertThat(providerCache.getSeatMap(ProviderType.MOCK, "TRIP-1")).contains(seatMap);
    }

    @Test
    void getSeatMapIsEmptyWhenUncached() {
        assertThat(providerCache.getSeatMap(ProviderType.MOCK, "UNCACHED-TRIP")).isEmpty();
    }
}
