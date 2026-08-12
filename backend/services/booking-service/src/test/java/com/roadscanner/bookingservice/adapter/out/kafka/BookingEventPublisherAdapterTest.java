package com.roadscanner.bookingservice.adapter.out.kafka;

import com.roadscanner.bookingservice.config.BookingProperties;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Contact;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The wire shape {@code notification-service} depends on.
 *
 * <p>A notification is worthless without a recipient, and the recipient exists only here —
 * {@code Contact} is part of this aggregate and no other service holds it. These tests pin that the
 * details actually reach the topic, because their absence would not fail anything in this service;
 * it would silently stop every traveller's confirmation email in another one.
 */
class BookingEventPublisherAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-12T09:00:00Z");
    private static final Instant DEPARTURE = Instant.parse("2026-08-13T20:00:00Z");

    /**
     * A capturing stand-in rather than a Mockito mock: {@code KafkaTemplate} cannot be mocked in
     * this project's test setup (the inline mock maker for final types is not enabled — the same
     * constraint {@code search-service}'s {@code SearchControllerTest} records).
     */
    private static final class CapturingKafkaTemplate extends KafkaTemplate<String, Object> {

        private final List<String> topics = new ArrayList<>();
        private final List<Object> payloads = new ArrayList<>();
        private CompletableFuture<SendResult<String, Object>> outcome =
                CompletableFuture.completedFuture(null);

        private CapturingKafkaTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of()));
        }

        private void failsWith(RuntimeException failure) {
            this.outcome = CompletableFuture.failedFuture(failure);
        }

        @Override
        public CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object data) {
            topics.add(topic);
            payloads.add(data);
            return outcome;
        }
    }

    private CapturingKafkaTemplate kafkaTemplate;
    private BookingEventPublisherAdapter publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = new CapturingKafkaTemplate();
        publisher = new BookingEventPublisherAdapter(kafkaTemplate, new BookingProperties(
                new BookingProperties.Kafka("catalog-trip-events", "provider-integration-events",
                        "payment-events", "booking-events"),
                new BookingProperties.Scheduling("0 * * * * *", "0 * * * * *")));
    }

    private Booking booking() {
        return Booking.create(new BookingId(UUID.randomUUID()), UUID.randomUUID(),
                new TripId(UUID.randomUUID()), DEPARTURE, new ProviderType("MOCK"),
                "MOCK-HYDERABAD-BENGALURU-2026-08-13-AC-SLEEPER", "MOCK-BLK-1", NOW.plusSeconds(300),
                List.of(new Passenger("Asha", "Rao", LocalDate.of(1994, 3, 11), "female", "L1")),
                new Contact("+919876543210", "traveller@example.com", Contact.CommunicationPreference.EMAIL),
                new Fare(new BigDecimal("899.00"), Currency.getInstance("INR")), NOW);
    }

    private BookingEventMessage capturePublished() {
        assertThat(kafkaTemplate.topics).containsExactly("booking-events");
        return (BookingEventMessage) kafkaTemplate.payloads.get(0);
    }

    @Test
    void aConfirmationCarriesEverythingANotificationNeeds() {
        Booking booking = booking();
        booking.confirm("RS-1234", "RS-1234-ORD", null, NOW);

        publisher.publishBookingConfirmed(booking, NOW);

        BookingEventMessage message = capturePublished();
        assertThat(message.eventType()).isEqualTo(BookingEventType.CONFIRMED);
        assertThat(message.contactEmail()).isEqualTo("traveller@example.com");
        assertThat(message.contactPhone()).isEqualTo("+919876543210");
        assertThat(message.communicationPreference()).isEqualTo("EMAIL");
        assertThat(message.bookingReference()).isEqualTo("RS-1234");
        assertThat(message.departureTime()).isEqualTo(DEPARTURE);
        assertThat(message.fareAmount()).isEqualByComparingTo("899.00");
        assertThat(message.fareCurrency()).isEqualTo("INR");
    }

    @Test
    void everyEventCarriesAFreshIdSoARedeliveryIsRecognisable() {
        Booking booking = booking();

        publisher.publishBookingCreated(booking, NOW);
        publisher.publishBookingCreated(booking, NOW);

        assertThat(kafkaTemplate.payloads).hasSize(2);
        BookingEventMessage first = (BookingEventMessage) kafkaTemplate.payloads.get(0);
        BookingEventMessage second = (BookingEventMessage) kafkaTemplate.payloads.get(1);

        // Two distinct publications, not a redelivery of one — so consumers must see two ids.
        assertThat(first.eventId()).isNotNull().isNotEqualTo(second.eventId());
    }

    @Test
    void aCancellationStatesItsReasonSoAPaymentFailureIsDistinguishable() {
        Booking booking = booking();
        booking.cancel(CancellationReason.PAYMENT_FAILED, NOW);

        publisher.publishBookingCancelled(booking, NOW);

        // This is what lets a consumer tell a declined card from a traveller changing their mind,
        // and send one accurate message instead of two vague ones.
        assertThat(capturePublished().cancellationReason()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void aBookingCancelledBeforeConfirmationCarriesNoReferenceRatherThanAPlaceholder() {
        Booking booking = booking();
        booking.cancel(CancellationReason.PAYMENT_FAILED, NOW);

        publisher.publishBookingCancelled(booking, NOW);

        assertThat(capturePublished().bookingReference()).isNull();
    }

    @Test
    void aBrokerFailureIsSwallowedBecauseTheBookingIsAlreadyDurable() {
        kafkaTemplate.failsWith(new IllegalStateException("broker down"));

        // The Postgres write happened before this was called. A lost publish costs the async
        // fan-out and nothing else — it must never turn a completed booking into a failed one.
        assertThatCode(() -> publisher.publishBookingConfirmed(booking(), NOW)).doesNotThrowAnyException();
    }
}
