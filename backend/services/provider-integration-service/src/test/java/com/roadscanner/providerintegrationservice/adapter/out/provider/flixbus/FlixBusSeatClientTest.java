package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderTripNotFoundException;
import com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FlixBusSeatClientTest {

    private static final String BASE_URL = "http://flixbus.test";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ProviderSession SESSION = ProviderSession.open(ProviderSessionId.generate(),
            ProviderType.FLIXBUS, new ProviderToken("token-123", null, "Bearer", Instant.parse("2026-08-01T00:00:00Z")),
            Instant.parse("2026-07-01T00:00:00Z"));

    private MockRestServiceServer mockServer;
    private FlixBusSeatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new FlixBusSeatClient(builder.build(), new FlixBusMapper(CLOCK), new FlixBusExceptionTranslator(CLOCK));
    }

    @Test
    void mapsASuccessfulSeatMapResponse() {
        mockServer.expect(requestTo(BASE_URL + "/v1/trips/FB-1/seatmap"))
                .andRespond(withSuccess("""
                        {"tripId": "FB-1", "seats": [{"seatNumber": "L1", "deck": "LOWER", "seatType": "AC Sleeper",
                        "status": "AVAILABLE", "price": {"amount": 500.00, "currency": "INR"}}]}
                        """, MediaType.APPLICATION_JSON));

        ProviderSeatMap seatMap = client.getSeatMap(SESSION, "FB-1");

        assertThat(seatMap.seats()).hasSize(1);
        assertThat(seatMap.seats().get(0).seatNumber()).isEqualTo(new SeatNumber("L1"));
    }

    @Test
    void seatMapFor404TranslatesToTripNotFound() {
        mockServer.expect(requestTo(BASE_URL + "/v1/trips/UNKNOWN/seatmap"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getSeatMap(SESSION, "UNKNOWN"))
                .isInstanceOf(ProviderTripNotFoundException.class);
    }

    @Test
    void blockConflictTranslatesToSeatUnavailable() {
        mockServer.expect(requestTo(BASE_URL + "/v1/trips/FB-1/seat-blocks"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.blockSeats(SESSION, "FB-1", List.of(new SeatNumber("L1"))))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void blockSuccessMapsToASeatReservation() {
        mockServer.expect(requestTo(BASE_URL + "/v1/trips/FB-1/seat-blocks"))
                .andRespond(withSuccess("""
                        {"blockReference": "FB-BLK-1", "seatNumbers": ["L1"], "expiresAtUtc": "2026-07-01T00:05:00Z"}
                        """, MediaType.APPLICATION_JSON));

        SeatReservation reservation = client.blockSeats(SESSION, "FB-1", List.of(new SeatNumber("L1")));

        assertThat(reservation.providerBlockReference()).isEqualTo("FB-BLK-1");
        assertThat(reservation.seatNumbers()).containsExactly(new SeatNumber("L1"));
    }
}
