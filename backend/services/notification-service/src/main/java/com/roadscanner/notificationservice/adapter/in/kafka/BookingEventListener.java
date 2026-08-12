package com.roadscanner.notificationservice.adapter.in.kafka;

import com.roadscanner.notificationservice.domain.port.in.SendBookingNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code booking-events} — the topic
 * {@code docs/services/booking-service/events-published.md} already names this service as a
 * consumer of.
 *
 * <p>Thin on purpose: translate, delegate, acknowledge. The decision of what to send lives in
 * {@link BookingEventTranslator} and the delivery in the application service, both of which are
 * testable without a broker.
 *
 * <p>Nothing escapes this method. A listener that throws gets the message redelivered, and every
 * failure this service can have — a refused mail server, a malformed event — would repeat
 * identically on redelivery while re-attempting a send. Delivery failures are already recorded in
 * the notification log, which is where an operator looks; an exception here would add a retry loop
 * against a customer's inbox and nothing else.
 */
@Component
class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final SendBookingNotification sendBookingNotification;
    private final BookingEventTranslator translator = new BookingEventTranslator();

    BookingEventListener(SendBookingNotification sendBookingNotification) {
        this.sendBookingNotification = sendBookingNotification;
    }

    @KafkaListener(topics = "${roadscanner.notification.kafka.booking-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "bookingEventListenerContainerFactory")
    void onBookingEvent(BookingEventMessage message) {
        try {
            translator.translate(message).ifPresentOrElse(
                    notification -> sendBookingNotification.send(new SendBookingNotification.Command(notification)),
                    () -> log.debug("Booking event {} for booking {} needs no notification",
                            message.eventType(), message.bookingId()));
        } catch (RuntimeException e) {
            // Deliberately terminal. See this class's Javadoc — the alternative is an endless
            // redelivery loop that re-sends to the customer each time.
            log.error("Failed to handle booking event {} for booking {} — not retrying",
                    message.eventType(), message.bookingId(), e);
        }
    }
}
