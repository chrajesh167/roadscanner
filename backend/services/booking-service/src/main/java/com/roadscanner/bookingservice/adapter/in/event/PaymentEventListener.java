package com.roadscanner.bookingservice.adapter.in.event;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentCompleted;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentFailed;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentTimedOut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code payment-service}'s payment-events topic — <strong>designed for, not yet
 * real</strong> (docs/services/booking-service/boundaries.md's "Relationship to
 * `payment-service`"). Dispatches on {@link PaymentEventType}, matching
 * {@code inventory-service}'s {@code TripEventListener} dispatch pattern exactly.
 */
@Component
class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final HandlePaymentCompleted handlePaymentCompleted;
    private final HandlePaymentFailed handlePaymentFailed;
    private final HandlePaymentTimedOut handlePaymentTimedOut;

    PaymentEventListener(HandlePaymentCompleted handlePaymentCompleted, HandlePaymentFailed handlePaymentFailed,
                          HandlePaymentTimedOut handlePaymentTimedOut) {
        this.handlePaymentCompleted = handlePaymentCompleted;
        this.handlePaymentFailed = handlePaymentFailed;
        this.handlePaymentTimedOut = handlePaymentTimedOut;
    }

    @KafkaListener(id = "payment-events-listener", topics = "${roadscanner.booking.kafka.payment-events-topic}",
            containerFactory = "paymentEventListenerContainerFactory")
    void onMessage(PaymentEventMessage message) {
        log.debug("Received {} for booking {}", message.eventType(), message.bookingId());
        BookingId bookingId = new BookingId(message.bookingId());
        switch (message.eventType()) {
            case COMPLETED -> handlePaymentCompleted.handle(new HandlePaymentCompleted.Command(bookingId,
                    message.paymentReference(), message.occurredAt()));
            case FAILED -> handlePaymentFailed.handle(
                    new HandlePaymentFailed.Command(bookingId, message.occurredAt()));
            case TIMED_OUT -> handlePaymentTimedOut.handle(
                    new HandlePaymentTimedOut.Command(bookingId, message.occurredAt()));
        }
    }
}
