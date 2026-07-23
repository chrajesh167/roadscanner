package com.roadscanner.bookingservice.adapter.in.event;

import com.roadscanner.bookingservice.domain.port.in.HandleSeatReleased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code provider-integration-service}'s audit topic, acting only on
 * {@code eventType = "SeatReleased"} — a message this service is not yet actually sent, since
 * that event isn't published by the current implementation
 * (docs/services/booking-service/events-consumed.md). Wired now so the contract is ready the
 * moment the producer catches up; {@code Sweep Stale Holds}
 * (docs/services/booking-service/use-cases.md) is the interim safety net until then.
 */
@Component
class SeatReleasedListener {

    private static final Logger log = LoggerFactory.getLogger(SeatReleasedListener.class);
    private static final String SEAT_RELEASED_EVENT_TYPE = "SeatReleased";

    private final HandleSeatReleased handleSeatReleased;

    SeatReleasedListener(HandleSeatReleased handleSeatReleased) {
        this.handleSeatReleased = handleSeatReleased;
    }

    @KafkaListener(id = "provider-integration-events-listener",
            topics = "${roadscanner.booking.kafka.provider-integration-events-topic}",
            containerFactory = "providerAuditEventListenerContainerFactory")
    void onMessage(ProviderAuditMessage message) {
        if (!SEAT_RELEASED_EVENT_TYPE.equals(message.eventType())) {
            log.debug("Ignoring {} — booking-service only reacts to SeatReleased", message.eventType());
            return;
        }
        handleSeatReleased.handle(
                new HandleSeatReleased.Command(message.providerBlockReference(), message.occurredAt()));
    }
}
