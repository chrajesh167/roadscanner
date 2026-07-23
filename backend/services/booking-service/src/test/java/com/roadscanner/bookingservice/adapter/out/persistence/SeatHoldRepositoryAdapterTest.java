package com.roadscanner.bookingservice.adapter.out.persistence;

import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/** Exercises {@link SeatHoldRepositoryAdapter} against a real Postgres (Testcontainers). */
@DataJpaTest
@Import({TestcontainersConfiguration.class, SeatHoldRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SeatHoldRepositoryAdapterTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    private SeatHoldRepositoryAdapter adapter;

    private SeatHold newHold(String providerBlockReference, Instant expiresAt) {
        return SeatHold.create(SeatHoldId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", providerBlockReference,
                List.of("L1", "L2"), new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), expiresAt, T0);
    }

    @Test
    void savesAndRoundTripsASeatHoldWithItsSeatNumbers() {
        SeatHold hold = newHold("block-ref-1", T0.plusSeconds(600));

        adapter.save(hold);

        SeatHold found = adapter.findById(hold.id()).orElseThrow();
        assertThat(found.seatNumbers()).containsExactlyInAnyOrder("L1", "L2");
        assertThat(found.fare().amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void findByProviderBlockReferenceLocatesTheHold() {
        SeatHold hold = newHold("block-ref-2", T0.plusSeconds(600));
        adapter.save(hold);

        assertThat(adapter.findByProviderBlockReference("block-ref-2")).isPresent();
        assertThat(adapter.findByProviderBlockReference("no-such-ref")).isEmpty();
    }

    @Test
    void deleteByIdRemovesTheHold() {
        SeatHold hold = newHold("block-ref-3", T0.plusSeconds(600));
        adapter.save(hold);

        adapter.deleteById(hold.id());

        assertThat(adapter.findById(hold.id())).isEmpty();
    }

    @Test
    void findAllExpiredBeforeOnlyReturnsExpiredHolds() {
        SeatHold expired = newHold("block-ref-4", T0.minusSeconds(100));
        SeatHold active = newHold("block-ref-5", T0.plusSeconds(600));
        adapter.save(expired);
        adapter.save(active);

        List<SeatHold> found = adapter.findAllExpiredBefore(T0);

        assertThat(found).extracting(SeatHold::id).containsExactly(expired.id());
    }
}
