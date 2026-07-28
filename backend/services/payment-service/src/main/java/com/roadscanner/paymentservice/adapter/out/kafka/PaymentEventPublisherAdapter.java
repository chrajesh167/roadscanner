package com.roadscanner.paymentservice.adapter.out.kafka;

import com.roadscanner.paymentservice.config.PaymentProperties;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.port.out.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Implements {@link PaymentEventPublisher} across two topics
 * (docs/services/payment-service/events-published.md):
 *
 * <ul>
 *   <li>{@code payment-events} — the business outcomes {@code booking-service} consumes
 *       ({@code COMPLETED}/{@code FAILED}/{@code TIMED_OUT}), keyed by booking id for per-booking
 *       ordering, in the exact shape its frozen consumer expects.</li>
 *   <li>{@code payment-lifecycle-events} — the informational {@code PaymentCreated} and the refund
 *       events, keyed by payment id, on a topic {@code booking-service} does not consume (its enum
 *       only knows the three business outcomes).</li>
 * </ul>
 *
 * A publish failure is logged, not thrown — the Postgres write is what makes the fact durable; a
 * lost publish loses only the async fan-out (the platform-wide convention).
 */
@Component
class PaymentEventPublisherAdapter implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisherAdapter.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentProperties properties;

    PaymentEventPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate, PaymentProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publishPaymentCreated(Payment payment, Instant occurredAt) {
        // Informational only — booking-service must never depend on this
        // (docs/services/payment-service/events-published.md).
        publishLifecycle(new PaymentLifecycleEventMessage(PaymentLifecycleEventType.CREATED, payment.id().value(),
                payment.bookingReference().value(), payment.travelerId(), payment.amount().amount(),
                payment.amount().currency().getCurrencyCode(), null, payment.status().name(), occurredAt),
                payment.id().value().toString());
    }

    @Override
    public void publishPaymentCompleted(Payment payment, Instant occurredAt) {
        publishBusiness(PaymentEventType.COMPLETED, payment, occurredAt);
    }

    @Override
    public void publishPaymentFailed(Payment payment, Instant occurredAt) {
        publishBusiness(PaymentEventType.FAILED, payment, occurredAt);
    }

    @Override
    public void publishPaymentTimedOut(Payment payment, Instant occurredAt) {
        publishBusiness(PaymentEventType.TIMED_OUT, payment, occurredAt);
    }

    @Override
    public void publishRefundInitiated(Refund refund, Instant occurredAt) {
        publishRefund(PaymentLifecycleEventType.REFUND_INITIATED, refund, occurredAt);
    }

    @Override
    public void publishRefundCompleted(Refund refund, Instant occurredAt) {
        publishRefund(PaymentLifecycleEventType.REFUND_COMPLETED, refund, occurredAt);
    }

    @Override
    public void publishRefundFailed(Refund refund, Instant occurredAt) {
        publishRefund(PaymentLifecycleEventType.REFUND_FAILED, refund, occurredAt);
    }

    private void publishBusiness(PaymentEventType eventType, Payment payment, Instant occurredAt) {
        PaymentEventMessage message = new PaymentEventMessage(eventType, payment.bookingReference().value(),
                payment.id().toString(), occurredAt, payment.id().value(), payment.amount().amount(),
                payment.amount().currency().getCurrencyCode());
        String topic = properties.kafka().paymentEventsTopic();
        send(topic, payment.bookingReference().value().toString(), message, eventType.name());
    }

    private void publishRefund(PaymentLifecycleEventType eventType, Refund refund, Instant occurredAt) {
        publishLifecycle(new PaymentLifecycleEventMessage(eventType, refund.paymentId().value(),
                refund.bookingReference().value(), null, refund.amount().amount(),
                refund.amount().currency().getCurrencyCode(), refund.id().value(), refund.status().name(), occurredAt),
                refund.paymentId().value().toString());
    }

    private void publishLifecycle(PaymentLifecycleEventMessage message, String key) {
        send(properties.kafka().paymentLifecycleEventsTopic(), key, message, message.eventType().name());
    }

    private void send(String topic, String key, Object message, String label) {
        kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Failed to publish {} to topic {}", label, topic, ex);
            }
        });
    }
}
