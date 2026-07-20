package com.roadscanner.providerintegrationservice.adapter.out.provider.mock;

import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seed data for {@link MockProviderDataStore} — two representative bus configurations
 * (AC Sleeper, Non-AC Seater), each with a deterministic seat layout. Kept separate from
 * {@link MockProviderDataStore} so the "what a demo trip looks like" data is easy to find and
 * change without touching the store's request-handling logic.
 */
final class MockProviderFixtures {

    static final List<BusConfig> BUS_CONFIGS = List.of(
            new BusConfig("AC-SLEEPER", "AC Sleeper", new BigDecimal("899.00"), 2, 15),
            new BusConfig("NONAC-SEATER", "Non-AC Seater", new BigDecimal("449.00"), 1, 20)
    );

    private static final Currency INR = Currency.getInstance("INR");

    private MockProviderFixtures() {
    }

    /** One seat per row per deck, numbered {@code <deck-letter><row>} (e.g. {@code L1}, {@code U1}
     * for a two-deck sleeper; {@code L1}..{@code L20} for a single-deck seater). Every seat
     * starts {@link SeatStatus#AVAILABLE} except one, held back as {@link SeatStatus#UNAVAILABLE}
     * to give tests/demos a deterministic already-taken seat to exercise
     * {@code SeatUnavailableException} against without mutating shared state. */
    static Map<String, ProviderSeat> buildSeatTemplate(BusConfig config) {
        Map<String, ProviderSeat> seats = new LinkedHashMap<>();
        List<String> deckLetters = config.decks() == 2 ? List.of("L", "U") : List.of("L");
        boolean unavailableAssigned = false;
        for (String deck : deckLetters) {
            for (int row = 1; row <= config.rowsPerDeck(); row++) {
                SeatNumber seatNumber = new SeatNumber(deck + row);
                SeatStatus status = SeatStatus.AVAILABLE;
                if (!unavailableAssigned && deck.equals(deckLetters.get(deckLetters.size() - 1)) && row == config.rowsPerDeck()) {
                    status = SeatStatus.UNAVAILABLE;
                    unavailableAssigned = true;
                }
                seats.put(seatNumber.value(), new ProviderSeat(seatNumber, deck.equals("L") ? "LOWER" : "UPPER",
                        config.seatType(), status, new FareAmount(config.baseFare(), INR)));
            }
        }
        return seats;
    }

    static List<ProviderSeat> asOrderedList(Map<String, ProviderSeat> seatTemplate) {
        return new ArrayList<>(seatTemplate.values());
    }

    record BusConfig(String suffix, String seatType, BigDecimal baseFare, int decks, int rowsPerDeck) {
    }
}
