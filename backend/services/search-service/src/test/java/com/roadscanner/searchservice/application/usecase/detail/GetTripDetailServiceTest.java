package com.roadscanner.searchservice.application.usecase.detail;

import com.roadscanner.searchservice.application.usecase.availability.AvailabilityOverlay;
import com.roadscanner.searchservice.domain.exception.TripNotFoundException;
import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.GetTripDetail;
import com.roadscanner.searchservice.testsupport.fakes.InMemoryAvailabilityCache;
import com.roadscanner.searchservice.testsupport.fakes.InMemorySearchableTripRepository;
import com.roadscanner.searchservice.testsupport.fakes.StubAvailabilityClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetTripDetailServiceTest {

    private final InMemorySearchableTripRepository repository = new InMemorySearchableTripRepository();
    private final StubAvailabilityClient client = new StubAvailabilityClient();
    private final GetTripDetailService service = new GetTripDetailService(
            repository, new AvailabilityOverlay(new InMemoryAvailabilityCache(), client));

    @Test
    void returnsTheIndexedTripWithAvailabilityOverlaid() {
        TripId tripId = new TripId(UUID.randomUUID());
        SearchableTrip trip = SearchableTrip.publish(tripId, new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z"));
        repository.save(trip);
        client.willReturn(tripId, AvailabilityStatus.of(9));

        GetTripDetail.GetTripDetailResult result = service.getDetail(new GetTripDetail.GetTripDetailCommand(tripId));

        assertThat(result.result().trip().tripId()).isEqualTo(tripId);
        assertThat(result.result().availability()).isEqualTo(AvailabilityStatus.of(9));
    }

    @Test
    void throwsTripNotFoundForAnUnindexedTrip() {
        TripId unknown = new TripId(UUID.randomUUID());

        assertThatThrownBy(() -> service.getDetail(new GetTripDetail.GetTripDetailCommand(unknown)))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void returnsACancelledTripsDetailRatherThanFailing() {
        TripId tripId = new TripId(UUID.randomUUID());
        SearchableTrip trip = SearchableTrip.publish(tripId, new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z"));
        trip.cancel(Instant.parse("2026-07-02T00:00:00Z"));
        repository.save(trip);

        GetTripDetail.GetTripDetailResult result = service.getDetail(new GetTripDetail.GetTripDetailCommand(tripId));

        assertThat(result.result().trip().bookable()).isFalse();
    }
}
