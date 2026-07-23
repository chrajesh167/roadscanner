package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** A fully-configurable {@link ProviderIntegrationClient} test double. Each field defaults to a
 * successful, deterministic response; override per test as needed. Set a field's exception
 * supplier-equivalent by having the function throw directly. */
public final class StubProviderIntegrationClient implements ProviderIntegrationClient {

    public Supplier<SeatMapView> seatMapResult = () -> new SeatMapView(List.of());
    public Function<List<String>, Reservation> blockSeatsResult = seatNumbers -> new Reservation(
            "reservation-1", "block-ref-1", seatNumbers, "BLOCKED",
            java.time.Instant.parse("2026-08-01T00:00:00Z"), java.time.Instant.parse("2026-08-01T00:10:00Z"));
    public Supplier<Boolean> releaseSeatResult = () -> true;
    public Supplier<BookingConfirmationView> confirmBookingResult = () -> new BookingConfirmationView(
            "provider-booking-ref-1", java.time.Instant.parse("2026-08-01T00:05:00Z"));
    public Supplier<TicketView> downloadTicketResult = () -> new TicketView("ticket-1", "PDF",
            "ticket-content".getBytes(), java.time.Instant.parse("2026-08-01T00:06:00Z"));

    public int releaseSeatCallCount = 0;
    public int confirmBookingCallCount = 0;

    @Override
    public SeatMapView getSeatMap(ProviderType providerType, String providerTripId) {
        return seatMapResult.get();
    }

    @Override
    public Reservation blockSeats(ProviderType providerType, String providerTripId, List<String> seatNumbers) {
        return blockSeatsResult.apply(seatNumbers);
    }

    @Override
    public boolean releaseSeat(ProviderType providerType, String providerBlockReference) {
        releaseSeatCallCount++;
        return releaseSeatResult.get();
    }

    @Override
    public BookingConfirmationView confirmBooking(ProviderType providerType, String providerTripId,
                                                    String providerBlockReference, List<Passenger> passengers) {
        confirmBookingCallCount++;
        return confirmBookingResult.get();
    }

    @Override
    public TicketView downloadTicket(ProviderType providerType, String providerBookingReference) {
        return downloadTicketResult.get();
    }
}
