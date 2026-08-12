package com.roadscanner.notificationservice.application.usecase;

import com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException;
import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;
import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.NotificationType;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.in.SendBookingNotification;
import com.roadscanner.notificationservice.testsupport.InMemoryNotificationLogRepository;
import com.roadscanner.notificationservice.testsupport.RecordingChannels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The delivery rules, stated where they can be checked without a broker or a mail server.
 *
 * <p>Two of these matter more than the rest: a redelivered event must not reach the customer twice,
 * and nothing that happens here may ever propagate out of this service — a booking that already
 * happened cannot be undone by a message that failed to send.
 */
class SendBookingNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T09:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("4b924121-56e1-433d-ba57-a37f3d41471d");

    private InMemoryNotificationLogRepository notificationLog;
    private RecordingChannels.Email email;
    private RecordingChannels.Sms sms;
    private SendBookingNotification service;

    @BeforeEach
    void setUp() {
        notificationLog = new InMemoryNotificationLogRepository();
        email = new RecordingChannels.Email();
        sms = new RecordingChannels.Sms();
        service = new SendBookingNotificationService(notificationLog, email, sms,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private BookingNotification notification(NotificationType type, NotificationChannel channel) {
        return notification(type, channel, UUID.randomUUID());
    }

    private BookingNotification notification(NotificationType type, NotificationChannel channel, UUID eventId) {
        Recipient recipient = channel == NotificationChannel.EMAIL
                ? new Recipient(NotificationChannel.EMAIL, "traveller@example.com")
                : new Recipient(NotificationChannel.SMS, "+919876543210");
        return new BookingNotification(eventId, BOOKING_ID, type, recipient, "RS-1234",
                Instant.parse("2026-08-13T20:00:00Z"), new BigDecimal("899.00"), "INR", NOW);
    }

    private Optional<NotificationRecord> send(BookingNotification notification) {
        return service.send(new SendBookingNotification.Command(notification));
    }

    @Nested
    class Delivery {

        @Test
        void sendsAConfirmationByEmailAndRecordsItSent() {
            Optional<NotificationRecord> record =
                    send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL));

            assertThat(email.sent).hasSize(1);
            assertThat(email.sent.get(0).recipient().value()).isEqualTo("traveller@example.com");
            assertThat(record).get().satisfies(written -> {
                assertThat(written.status()).isEqualTo(NotificationStatus.SENT);
                assertThat(written.sentAt()).contains(NOW);
                assertThat(written.failureReason()).isEmpty();
            });
            assertThat(sms.sent).isEmpty();
        }

        @Test
        void sendsBySmsWhenThatIsTheChannelAndUsesTheNumberFromTheBooking() {
            send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.SMS));

            assertThat(sms.sent).hasSize(1);
            assertThat(sms.sent.get(0).recipient().value()).isEqualTo("+919876543210");
            assertThat(email.sent).isEmpty();
        }

        @Test
        void recordsWhatTheSmsAdapterActuallyAchievedRatherThanAssumingItWasSent() {
            // The demo adapter reports DEMO_RECORDED. Recording that as SENT would put a row in the
            // log claiming a carrier delivered a message that was never handed to one.
            sms.reportsOutcome(NotificationStatus.DEMO_RECORDED);

            Optional<NotificationRecord> record =
                    send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.SMS));

            assertThat(record).get().extracting(NotificationRecord::status)
                    .isEqualTo(NotificationStatus.DEMO_RECORDED);
        }

        @Test
        void recordsARealProviderDeliveryAsSent() {
            sms.reportsOutcome(NotificationStatus.SENT);

            Optional<NotificationRecord> record =
                    send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.SMS));

            assertThat(record).get().extracting(NotificationRecord::status).isEqualTo(NotificationStatus.SENT);
        }
    }

    @Nested
    class Idempotency {

        @Test
        void aRedeliveredEventDoesNotReachTheCustomerTwice() {
            UUID eventId = UUID.randomUUID();
            BookingNotification first = notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL, eventId);
            BookingNotification redelivery = notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL, eventId);

            send(first);
            Optional<NotificationRecord> second = send(redelivery);

            // Kafka is at-least-once, so this is the ordinary case rather than an edge one.
            assertThat(email.sent).hasSize(1);
            assertThat(second).isEmpty();
            assertThat(notificationLog.all()).hasSize(1);
        }

        @Test
        void theSameEventMayStillBeDeliveredOnceOnEachChannel() {
            UUID eventId = UUID.randomUUID();

            send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL, eventId));
            send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.SMS, eventId));

            // Idempotency is per channel: a traveller wanting both is two deliveries, not a duplicate.
            assertThat(email.sent).hasSize(1);
            assertThat(sms.sent).hasSize(1);
            assertThat(notificationLog.all()).hasSize(2);
        }

        @Test
        void aFailedNotificationIsNotRetriedByARedelivery() {
            UUID eventId = UUID.randomUUID();
            email.failWith(new NotificationDeliveryException("SMTP server rejected the message"));
            send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL, eventId));

            Optional<NotificationRecord> second =
                    send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL, eventId));

            // The claim is held whatever the outcome. Re-sending on redelivery risks a duplicate
            // for a message that may in fact have gone out before the failure was reported.
            assertThat(second).isEmpty();
            assertThat(notificationLog.findByEventAndChannel(eventId, NotificationChannel.EMAIL))
                    .get().extracting(NotificationRecord::status).isEqualTo(NotificationStatus.FAILED);
        }
    }

    @Nested
    class FailureContainment {

        @Test
        void aRefusedMailServerIsRecordedAndNeverThrown() {
            email.failWith(new NotificationDeliveryException("SMTP server rejected the message"));

            Optional<NotificationRecord> record =
                    send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL));

            assertThat(record).get().satisfies(written -> {
                assertThat(written.status()).isEqualTo(NotificationStatus.FAILED);
                assertThat(written.failureReason()).contains("SMTP server rejected the message");
            });
        }

        @Test
        void anAdapterThrowingSomethingUnexpectedStillDoesNotEscape() {
            // A bug in an adapter is still not allowed to reach the Kafka listener, because there it
            // would become a redelivery — and a redelivery re-attempts a send.
            email.failWith(new IllegalStateException("connection pool exhausted"));

            assertThatCode(() -> send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL)))
                    .doesNotThrowAnyException();

            assertThat(notificationLog.all()).singleElement()
                    .extracting(NotificationRecord::status).isEqualTo(NotificationStatus.FAILED);
        }

        @Test
        void aFailedNotificationLeavesTheBookingUntouchedBecauseNothingHereCanTouchIt() {
            email.failWith(new NotificationDeliveryException("mail server unreachable"));

            send(notification(NotificationType.BOOKING_CONFIRMED, NotificationChannel.EMAIL));

            // The strongest form of the guarantee: this service holds no booking state and no
            // outbound port that could change any. The only thing a failure writes is its own log.
            assertThat(notificationLog.all()).singleElement()
                    .extracting(NotificationRecord::bookingId).isEqualTo(BOOKING_ID);
        }
    }
}
