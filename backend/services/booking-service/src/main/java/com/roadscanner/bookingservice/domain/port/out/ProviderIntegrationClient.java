package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The only way this service ever acts on live seat state — every call crosses to
 * {@code provider-integration-service}'s existing, frozen internal REST API
 * (docs/services/booking-service/boundaries.md's "Relationship to
 * `provider-integration-service`"). Session authenticate-or-reuse is the adapter's own internal
 * concern, exactly matching {@code inventory-service}'s identical
 * {@code ProviderIntegrationClient} port shape — callers here never see a session id.
 *
 * <p>Unlike {@code inventory-service}'s "degrade, not fail" version of this port, every method
 * here throws rather than degrading: {@link com.roadscanner.bookingservice.domain.exception.SeatUnavailableException}
 * for a genuine provider rejection (a real business outcome — the provider's own accept/reject is
 * authoritative, docs/architecture/seat-locking-flow.md), or
 * {@link com.roadscanner.bookingservice.domain.exception.UpstreamServiceUnavailableException} for
 * anything else (timeout, 5xx, connection failure) — NFR-7 forbids a silent "unknown, proceed
 * anyway" outcome on this path.
 */
public interface ProviderIntegrationClient {

    SeatMapView getSeatMap(ProviderType providerType, String providerTripId);

    Reservation blockSeats(ProviderType providerType, String providerTripId, List<String> seatNumbers);

    /** Idempotent — releasing an already-released block is a no-op, matching
     * {@code provider-integration-service}'s own {@code ReleaseSeat}. */
    boolean releaseSeat(ProviderType providerType, String providerBlockReference);

    BookingConfirmationView confirmBooking(ProviderType providerType, String providerTripId,
                                            String providerBlockReference, List<Passenger> passengers);

    TicketView downloadTicket(ProviderType providerType, String providerBookingReference);

    record SeatMapView(List<SeatStatusView> seats) {
    }

    record SeatStatusView(String seatNumber, String deck, String seatType, String status,
                           BigDecimal priceAmount, String priceCurrency) {
    }

    record Reservation(String reservationId, String providerBlockReference, List<String> seatNumbers,
                        String status, Instant blockedAt, Instant expiresAt) {
    }

    record BookingConfirmationView(String providerBookingReference, Instant confirmedAt) {
    }

    record TicketView(String providerTicketId, String format, byte[] content, Instant issuedAt) {
    }
}
