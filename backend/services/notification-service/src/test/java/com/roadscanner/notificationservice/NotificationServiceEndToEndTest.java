package com.roadscanner.notificationservice;

import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;
import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.NotificationType;
import com.roadscanner.notificationservice.domain.port.out.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The path this service exists for, exercised against a real broker and a real database:
 * a booking event is published, and a notification row appears.
 *
 * <p>The unit tests prove the rules; this proves the wiring — that the published JSON actually
 * deserializes into this service's own copy of the message, that the listener is subscribed to the
 * right topic, and above all that the uniqueness constraint in Postgres is what stops a redelivery
 * becoming a second email. That last one cannot be shown with an in-memory fake, because the fake
 * is the thing being trusted.
 *
 * <p>No SMTP host is configured here, so email deliveries record {@code FAILED} with "no SMTP host
 * configured". That is the intended behaviour under test: the pipeline runs end to end and reports
 * honestly rather than pretending to have sent something.
 */
@SpringBootTest
@Import(com.roadscanner.notificationservice.testsupport.TestKafkaProducerConfig.class)
@Testcontainers
@ActiveProfiles("test")
class NotificationServiceEndToEndTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    private static final String TOPIC = "booking-events";

    @DynamicPropertySource
    static void topic(DynamicPropertyRegistry registry) {
        registry.add("roadscanner.notification.kafka.booking-events-topic", () -> TOPIC);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private NotificationLogRepository notificationLog;

    /** The producer's shape, as booking-service publishes it. */
    private Map<String, Object> bookingEvent(UUID eventId, UUID bookingId, String eventType,
                                              String cancellationReason, String preference) {
        return Map.ofEntries(
                Map.entry("eventType", eventType),
                Map.entry("bookingId", bookingId.toString()),
                Map.entry("travelerId", UUID.randomUUID().toString()),
                Map.entry("tripId", UUID.randomUUID().toString()),
                Map.entry("status", eventType),
                Map.entry("cancellationReason", cancellationReason == null ? "" : cancellationReason),
                Map.entry("occurredAt", Instant.parse("2026-08-12T09:00:00Z").toString()),
                Map.entry("eventId", eventId.toString()),
                Map.entry("bookingReference", "RS-1234"),
                Map.entry("contactEmail", "traveller@example.com"),
                Map.entry("contactPhone", "+919876543210"),
                Map.entry("communicationPreference", preference),
                Map.entry("departureTime", Instant.parse("2026-08-13T20:00:00Z").toString()),
                Map.entry("fareAmount", new BigDecimal("899.00")),
                Map.entry("fareCurrency", "INR"));
    }

    private NotificationRecord awaitRecord(UUID eventId, NotificationChannel channel) {
        return await().atMost(Duration.ofSeconds(30)).until(
                () -> notificationLog.findByEventAndChannel(eventId, channel), Optional::isPresent).orElseThrow();
    }

    @Test
    void aConfirmedBookingProducesAConfirmationNotification() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, bookingId.toString(),
                bookingEvent(eventId, bookingId, "CONFIRMED", null, "EMAIL"));

        NotificationRecord record = awaitRecord(eventId, NotificationChannel.EMAIL);
        assertThat(record.type()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(record.bookingId()).isEqualTo(bookingId);
        // No SMTP configured in this profile, so the honest outcome is a recorded failure.
        assertThat(record.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(record.failureReason()).get().asString().contains("NOTIFICATION_EMAIL_HOST");
    }

    @Test
    void aPaymentInducedCancellationProducesOnePaymentFailureNotification() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, bookingId.toString(),
                bookingEvent(eventId, bookingId, "CANCELLED", "PAYMENT_FAILED", "EMAIL"));

        NotificationRecord record = awaitRecord(eventId, NotificationChannel.EMAIL);
        assertThat(record.type()).isEqualTo(NotificationType.PAYMENT_FAILED);
        // And nothing on the other channel: one incident, one message.
        assertThat(notificationLog.findByEventAndChannel(eventId, NotificationChannel.SMS)).isEmpty();
    }

    @Test
    void anExplicitCancellationProducesACancellationNotification() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, bookingId.toString(),
                bookingEvent(eventId, bookingId, "CANCELLED", "TRAVELER_REQUESTED", "EMAIL"));

        assertThat(awaitRecord(eventId, NotificationChannel.EMAIL).type())
                .isEqualTo(NotificationType.BOOKING_CANCELLED);
    }

    @Test
    void anSmsPreferenceIsRecordedAsADemoDeliveryRatherThanASend() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, bookingId.toString(),
                bookingEvent(eventId, bookingId, "CONFIRMED", null, "SMS"));

        NotificationRecord record = awaitRecord(eventId, NotificationChannel.SMS);
        assertThat(record.status()).isEqualTo(NotificationStatus.DEMO_RECORDED);
        assertThat(notificationLog.findByEventAndChannel(eventId, NotificationChannel.EMAIL)).isEmpty();
    }

    @Test
    void aRedeliveredEventDoesNotProduceASecondNotification() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Map<String, Object> event = bookingEvent(eventId, bookingId, "CONFIRMED", null, "SMS");

        kafkaTemplate.send(TOPIC, bookingId.toString(), event);
        NotificationRecord first = awaitRecord(eventId, NotificationChannel.SMS);

        // The identical message again, exactly as an at-least-once broker would redeliver it.
        kafkaTemplate.send(TOPIC, bookingId.toString(), event);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationLog.findByEventAndChannel(eventId, NotificationChannel.SMS))
                        .get().extracting(NotificationRecord::id).isEqualTo(first.id()));
    }

    @Test
    void aBookingAwaitingPaymentProducesNoNotificationAtAll() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, bookingId.toString(),
                bookingEvent(eventId, bookingId, "CREATED", null, "EMAIL"));

        await().during(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(notificationLog.findByEventAndChannel(eventId, NotificationChannel.EMAIL)).isEmpty();
            assertThat(notificationLog.findByEventAndChannel(eventId, NotificationChannel.SMS)).isEmpty();
        });
    }
}
