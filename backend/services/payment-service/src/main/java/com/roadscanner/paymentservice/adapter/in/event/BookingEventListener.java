package com.roadscanner.paymentservice.adapter.in.event;

import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.port.in.ReconcileCancelledBooking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code booking-service}'s {@code booking-events} topic. Acts <strong>only</strong> on
 * {@code CANCELLED}, and only for reconciliation — never to initiate a refund
 * (docs/services/payment-service/events-consumed.md's "The Refund-Trigger Reconciliation"). The
 * authoritative refund trigger is the synchronous internal Refund API. {@code CREATED}/{@code CONFIRMED}
 * are ignored (they carry no payment action), matching {@code booking-service}'s own dispatch style.
 */
@Component
class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final ReconcileCancelledBooking reconcileCancelledBooking;

    BookingEventListener(ReconcileCancelledBooking reconcileCancelledBooking) {
        this.reconcileCancelledBooking = reconcileCancelledBooking;
    }

    @KafkaListener(id = "booking-events-listener", topics = "${roadscanner.payment.kafka.booking-events-topic}",
            containerFactory = "bookingEventListenerContainerFactory")
    void onMessage(BookingEventMessage message) {
        if (message.eventType() != BookingEventType.CANCELLED) {
            return;
        }
        log.debug("Reconciling cancelled booking {} (reason {})", message.bookingId(), message.cancellationReason());
        reconcileCancelledBooking.reconcile(new ReconcileCancelledBooking.Command(
                new BookingReference(message.bookingId()), message.cancellationReason(), message.occurredAt()));
    }
}
