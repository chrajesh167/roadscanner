package com.roadscanner.providerintegrationservice.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.providerintegrationservice.adapter.in.rest.search.SearchTripsResponse;
import com.roadscanner.providerintegrationservice.adapter.in.rest.seatmap.SeatMapResponse;
import com.roadscanner.providerintegrationservice.adapter.in.rest.session.AuthenticateProviderResponse;
import com.roadscanner.providerintegrationservice.config.JacksonConfig;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatStatus;
import com.roadscanner.providerintegrationservice.testsupport.ServiceContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The producer half of the inter-service contract: proves that what this service actually
 * serializes is byte-for-byte the payload checked into {@code backend/contracts}, which the
 * consuming services bind against in their own suites.
 *
 * <p>Built from real domain objects through the real response mappers and the real
 * {@link JacksonConfig} mapper — not from a hand-written literal. A literal would only prove that
 * a test agrees with a test.
 *
 * <p>Each case asserts the field <em>names</em> explicitly as well as comparing trees. Tree
 * equality already catches a rename, but naming the fields makes the failure say which field moved
 * instead of dumping two JSON documents and leaving the reader to diff them. This is the check
 * that would have caught {@code busType} → {@code serviceClass} breaking inventory-service.
 */
class ProviderIntegrationContractTest {

    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    private static final Instant DEPARTURE = Instant.parse("2026-08-01T08:00:00Z");
    private static final Instant ARRIVAL = Instant.parse("2026-08-01T14:00:00Z");
    private static final ProviderType FLIXBUS = new ProviderType("FLIXBUS");
    private static final Currency INR = Currency.getInstance("INR");

    /**
     * Serializes to a real string and re-parses, rather than using {@code valueToTree}: tree
     * conversion strips trailing zeros from a {@code BigDecimal}, so it would compare something
     * other than the bytes an HTTP response actually carries.
     */
    private JsonNode serialized(Object payload) {
        try {
            return mapper.readTree(wire(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + payload.getClass().getSimpleName(), e);
        }
    }

    private String wire(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + payload.getClass().getSimpleName(), e);
        }
    }

    private JsonNode contract(String name) {
        try {
            return mapper.readTree(ServiceContract.json("provider-integration-service/" + name));
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable contract " + name, e);
        }
    }

    @Test
    void searchTripsResponseMatchesThePublishedContract() {
        ProviderTrip populated = new ProviderTrip("ride-1", FLIXBUS, "FlixBus", "Hyderabad", "Pune",
                DEPARTURE, ARRIVAL, "AC Sleeper", new FareAmount(new BigDecimal("899.00"), INR), 12,
                "point-a", "point-b");

        // The optional half of the contract: a provider that reports neither a service tier nor
        // separate boarding points, and a sold-out trip. Consumers must bind these as nulls and a
        // zero rather than dropping the trip.
        ProviderTrip sparse = new ProviderTrip("ride-2", FLIXBUS, "FlixBus", "Hyderabad", "Pune",
                Instant.parse("2026-08-01T21:30:00Z"), Instant.parse("2026-08-02T05:15:00Z"), null,
                new FareAmount(new BigDecimal("1250.50"), INR), 0, null, null);

        SearchTripsResponse response = SearchTripsResponse.fromTrips(List.of(populated, sparse));
        JsonNode actual = serialized(response);

        assertThat(actual).isEqualTo(contract("search-trips-response.json"));

        // Fares are money: asserted on the raw wire text because a parsed JSON number compares
        // equal at 899.0, so a scale change would slip past the tree comparison above.
        assertThat(wire(response)).contains("\"fareAmount\":899.00").contains("\"fareAmount\":1250.50");
        assertThat(actual.get("trips").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "providerTripId", "providerType", "operatorName", "origin", "destination",
                "departureTime", "arrivalTime", "serviceClass", "fareAmount", "fareCurrency",
                "seatsAvailable", "boardingPointId", "alightingPointId");
    }

    @Test
    void authenticateProviderResponseMatchesThePublishedContract() {
        AuthenticateProviderResponse response = new AuthenticateProviderResponse(
                UUID.fromString("6f1c9c2e-1f4d-4a3b-9f0e-2c7b8a5d1e40"), "FLIXBUS",
                Instant.parse("2026-08-01T09:00:00Z"));

        JsonNode actual = serialized(response);

        assertThat(actual).isEqualTo(contract("authenticate-provider-response.json"));
        assertThat(actual.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("sessionId", "providerType", "expiresAt");
    }

    @Test
    void seatMapResponseMatchesThePublishedContract() {
        ProviderSeatMap seatMap = new ProviderSeatMap("ride-1", FLIXBUS, List.of(
                seat("1A", "LOWER", "SLEEPER", SeatStatus.AVAILABLE, "899.00"),
                seat("1B", "LOWER", "SLEEPER", SeatStatus.BOOKED, "899.00"),
                seat("2A", "UPPER", "SEATER", SeatStatus.AVAILABLE, "650.00")));

        JsonNode actual = serialized(SeatMapResponse.from(seatMap));

        assertThat(actual).isEqualTo(contract("seat-map-response.json"));
        assertThat(actual.get("seats").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "seatNumber", "deck", "seatType", "status", "priceAmount", "priceCurrency");
    }

    @Test
    void seatStatusIsPublishedAsItsNameSoConsumersCanMatchOnIt() {
        // inventory-service counts availability by string-matching "AVAILABLE". Publishing the
        // enum as an ordinal, or renaming the constant, would silently report every trip sold out.
        assertThat(serialized(SeatMapResponse.from(new ProviderSeatMap("ride-1", FLIXBUS,
                List.of(seat("1A", "LOWER", "SLEEPER", SeatStatus.AVAILABLE, "899.00")))))
                .get("seats").get(0).get("status").asText()).isEqualTo("AVAILABLE");
    }

    private static ProviderSeat seat(String number, String deck, String type, SeatStatus status, String price) {
        return new ProviderSeat(new SeatNumber(number), deck, type, status,
                new FareAmount(new BigDecimal(price), INR));
    }
}
