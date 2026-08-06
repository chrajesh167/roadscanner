package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.Contact;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    /**
     * Holds seats for named travellers.
     *
     * <p>Takes passengers rather than seat numbers because a hold binds a seat to its occupant:
     * FlixBus's seat-reservation call carries a gender per reserved seat so gender-restricted seats
     * can be honoured, and {@code provider-integration-service}'s {@code BlockSeatRequest} takes
     * the whole traveller. Each passenger carries its own seat, so the two cannot fall out of step
     * the way parallel lists do.
     */
    Reservation blockSeats(ProviderType providerType, String providerTripId, List<Passenger> passengers);

    /** Idempotent — releasing an already-released block is a no-op, matching
     * {@code provider-integration-service}'s own {@code ReleaseSeat}. */
    boolean releaseSeat(ProviderType providerType, String providerBlockReference);

    /**
     * Confirms the held booking.
     *
     * <p>No trip id: the provider reads the trip from the stored hold the block reference
     * identifies, and rejects a caller-supplied one outright — it could disagree with the
     * departure the seats were actually held against, and the provider would confirm regardless.
     * The contact is where the ticket is sent, and the provider requires it.
     */
    BookingConfirmationView confirmBooking(ProviderType providerType, String providerBlockReference,
                                            Contact contact, List<Passenger> passengers);

    /**
     * Fetches the provider's ticket, where the provider issues one at all.
     *
     * <p>Returns empty rather than throwing when the provider has no ticket-download capability.
     * This is the difference between "this provider does not issue tickets" and "this provider is
     * broken", and conflating them is what caused every FlixBus booking to be cancelled and
     * refunded moments after it was successfully paid for.
     */
    Optional<TicketView> downloadTicket(ProviderType providerType, String providerBookingReference);

    /**
     * Cancels the confirmed order with the provider that issued it.
     *
     * <p>Keyed by the provider's order reference alone: the token authorising cancellation belongs
     * to {@code provider-integration-service}, which stored it at confirmation and resolves it
     * itself. This service neither holds nor wants a provider credential.
     *
     * <p>Returns the amount the provider actually refunded, which is not necessarily the fare —
     * the provider's own cancellation policy decides it, and that figure is what any RoadScanner
     * refund must reconcile against.
     */
    CancellationView cancelBooking(ProviderType providerType, String providerBookingReference, String reason);

    record SeatMapView(List<SeatStatusView> seats) {
    }

    record SeatStatusView(String seatNumber, String deck, String seatType, String status,
                           BigDecimal priceAmount, String priceCurrency) {
    }

    record Reservation(String reservationId, String providerBlockReference, List<String> seatNumbers,
                        String status, Instant blockedAt, Instant expiresAt) {
    }

    /**
     * @param providerCheckoutReference the pre-order handle, where the provider issues one; the
     *                                  only handle support can use if an order lookup ever fails
     */
    record BookingConfirmationView(String providerBookingReference, String providerCheckoutReference,
                                    Instant confirmedAt) {
    }

    record CancellationView(String providerOrderReference, BigDecimal refundedAmount, String refundedCurrency,
                             Instant cancelledAt) {
    }

    record TicketView(String providerTicketId, String format, byte[] content, Instant issuedAt) {
    }
}
