package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderResponseException;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import com.roadscanner.providerintegrationservice.domain.model.SeatAssignment;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Sequences FlixBus's multi-call block and booking flows into the two operations the platform port
 * exposes.
 *
 * <p>FlixBus has no single "block seats" or "confirm booking" endpoint. A block is three calls
 * (cart, tickets, seat reservation) and a booking is four (checkout, verify, pay, poll). The
 * ordering, and what each call needs from the one before it, is the integration's real complexity —
 * so it lives here, in one readable sequence, rather than being spread across the HTTP clients.
 * Each {@code FlixBus*Client} stays a thin, testable wrapper over one endpoint.
 *
 * <p>The clients know how to call FlixBus; this class knows what order to call it in and why.
 */
@Component
class FlixBusBookingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FlixBusBookingOrchestrator.class);

    /**
     * How long a cart is treated as holding seats.
     *
     * <p>The documented API states no cart lifetime, so this is RoadScanner's own conservative
     * horizon for offering a hold to a traveller — not a claim about FlixBus's behaviour. Held
     * short deliberately: promising a longer hold than the provider honours is the failure that
     * strands someone at checkout.
     */
    private static final Duration BLOCK_HOLD = Duration.ofMinutes(10);

    private final FlixBusSeatMapClient seatMapClient;
    private final FlixBusCartClient cartClient;
    private final FlixBusCheckoutClient checkoutClient;
    private final FlixBusPaymentClient paymentClient;
    private final FlixBusMapper mapper;
    private final FlixBusProperties properties;
    private final Clock clock;

    FlixBusBookingOrchestrator(FlixBusSeatMapClient seatMapClient, FlixBusCartClient cartClient,
                               FlixBusCheckoutClient checkoutClient, FlixBusPaymentClient paymentClient,
                               FlixBusMapper mapper, FlixBusProperties properties, Clock clock) {
        this.seatMapClient = seatMapClient;
        this.cartClient = cartClient;
        this.checkoutClient = checkoutClient;
        this.paymentClient = paymentClient;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Endpoints 4, 5, 6 — hold the requested seats.
     *
     * <p>Callers name seats by the label a traveller sees ("12"); FlixBus reserves by its own
     * per-seat UUID, which exists only in the seat map. So the map is re-read here to resolve them.
     * That is one extra documented call rather than a guess: there is no way to derive a seat id
     * from a label, and carrying ids through every layer would leak a provider's internal
     * identifiers into models shared by providers that have no such concept.
     */
    SeatReservation blockSeats(Provider provider, FlixBusTripUid tripUid, List<SeatNumber> seatNumbers,
                               List<PassengerDetail> passengers) {
        Map<String, String> seatIdsByLabel = seatMapClient.seatIdsByLabel(provider, tripUid);

        List<String> unknown = seatNumbers.stream()
                .map(SeatNumber::value)
                .filter(label -> !seatIdsByLabel.containsKey(label))
                .toList();
        if (!unknown.isEmpty()) {
            // Naming the seats beats a provider-side rejection that says only "invalid seat".
            throw new ProviderResponseException(ProviderType.FLIXBUS, "blockSeats",
                    "Seat(s) " + String.join(", ", unknown) + " are not on this ride's seat map", null);
        }

        String cartId = cartClient.createCart(provider);
        List<String> ticketIds = cartClient.addTickets(provider, cartId, tripUid, seatNumbers.size(), 0);

        // Positional pairing: ticket i belongs to passenger i and gets seat i. The counts are
        // verified when the tickets are created, so the three lists are the same length here.
        List<SeatAssignment> assignments = java.util.stream.IntStream.range(0, seatNumbers.size())
                .mapToObj(i -> new SeatAssignment(seatNumbers.get(i),
                        seatIdsByLabel.get(seatNumbers.get(i).value()), ticketIds.get(i)))
                .toList();

        cartClient.reserveSeats(provider, cartId, tripUid, assignments, passengers);

        Instant now = clock.instant();
        return SeatReservation.block(ReservationId.generate(), ProviderType.FLIXBUS, cartId, tripUid.value(),
                assignments, now, now.plus(BLOCK_HOLD));
    }

    /**
     * Endpoints 7, 8, 9, 8 — turn a held cart into a paid, confirmed order.
     *
     * <p>The checkout is read twice on purpose. Once before payment, because the fare FlixBus will
     * charge is authoritative and must be reconciled before money moves. Once after, repeatedly,
     * because FlixBus materializes the order asynchronously — the order id and token that make the
     * booking cancellable simply are not there yet when payment returns.
     */
    BookingConfirmation confirmBooking(Provider provider, SeatReservation reservation, ContactDetail contact,
                                       List<PassengerDetail> passengers) {
        FlixBusTripUid tripUid = FlixBusTripUid.parse(reservation.providerTripId());
        String cartId = reservation.providerBlockReference();

        String checkoutId = checkoutClient.checkout(provider, cartId, contact,
                reservation.providerTicketIds(), passengers);

        FlixBusMapper.CheckoutDetails beforePayment = checkoutClient.getCheckout(provider, checkoutId);
        log.info("FlixBus checkout {} ready for payment: total={} trip={}",
                checkoutId, beforePayment.total(), tripUid.rideId());

        paymentClient.pay(provider, checkoutId);

        FlixBusMapper.CheckoutDetails settled = awaitOrder(provider, checkoutId);

        BigDecimal total = settled.total().signum() > 0 ? settled.total() : beforePayment.total();
        Instant now = clock.instant();

        return new BookingConfirmation(
                // The order reference is what a traveller and support quote, so it is the booking
                // reference rather than a second identifier nobody outside this service can resolve.
                new BookingReference(settled.orderId()),
                reservation.reservationId(),
                reservation.providerTripId(),
                passengers,
                new FareAmount(total, Currency.getInstance(properties.currency())),
                checkoutId,
                settled.orderId(),
                settled.orderTokenIfPresent().orElse(null),
                now);
    }

    /**
     * Polls the checkout until the order appears.
     *
     * <p>Payment has already succeeded by the time this runs, so giving up here does not undo
     * anything — it means the booking exists at FlixBus but its reference was not captured. That is
     * a paid booking nobody can cancel, so the failure is loud and names the checkout id, which is
     * the one handle support can still use to find it.
     */
    private FlixBusMapper.CheckoutDetails awaitOrder(Provider provider, String checkoutId) {
        FlixBusMapper.CheckoutDetails details = null;

        for (int attempt = 1; attempt <= properties.checkoutPollAttempts(); attempt++) {
            details = checkoutClient.getCheckout(provider, checkoutId);
            if (details.hasOrder()) {
                return details;
            }
            if (attempt < properties.checkoutPollAttempts()) {
                sleep(properties.checkoutPollDelay());
            }
        }

        throw new ProviderResponseException(ProviderType.FLIXBUS, "confirmBooking",
                "Payment succeeded but FlixBus did not publish an order for checkout " + checkoutId
                        + " within " + properties.checkoutPollAttempts() + " attempts", null);
    }

    private void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderResponseException(ProviderType.FLIXBUS, "confirmBooking",
                    "Interrupted while waiting for FlixBus to publish the order", e);
        }
    }
}
