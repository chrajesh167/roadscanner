package com.roadscanner.notificationservice.adapter.in.kafka;

import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which booking events become which notifications, and on what channel.
 *
 * <p>The case worth reading first is {@link PaymentFailure}: one declined payment produces both a
 * {@code payment-events/FAILED} and a {@code booking-events/CANCELLED}, and the customer must
 * receive exactly one message describing what actually happened.
 */
class BookingEventTranslatorTest {

    private final BookingEventTranslator translator = new BookingEventTranslator();

    private BookingEventMessage event(String eventType, String cancellationReason, String preference) {
        return new BookingEventMessage(eventType, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                eventType, cancellationReason, Instant.parse("2026-08-12T09:00:00Z"), UUID.randomUUID(),
                "RS-1234", "traveller@example.com", "+919876543210", preference,
                Instant.parse("2026-08-13T20:00:00Z"), new BigDecimal("899.00"), "INR");
    }

    @Nested
    class Confirmation {

        @Test
        void aConfirmedBookingNotifiesTheTraveller() {
            Optional<BookingNotification> notification = translator.translate(event("CONFIRMED", null, "EMAIL"));

            assertThat(notification).get().satisfies(result -> {
                assertThat(result.type()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
                assertThat(result.bookingReference()).isEqualTo("RS-1234");
                assertThat(result.fareAmount()).isEqualByComparingTo("899.00");
            });
        }

        @Test
        void aBookingAwaitingPaymentNotifiesNobody() {
            // The traveller is still inside the flow that created it — mailing them about a step
            // they are watching happen is noise.
            assertThat(translator.translate(event("CREATED", null, "EMAIL"))).isEmpty();
        }
    }

    @Nested
    class PaymentFailure {

        @ParameterizedTest
        @ValueSource(strings = {"PAYMENT_FAILED", "PAYMENT_TIMED_OUT"})
        void aPaymentInducedCancellationIsReportedAsAPaymentFailureNotACancellation(String reason) {
            Optional<BookingNotification> notification = translator.translate(event("CANCELLED", reason, "EMAIL"));

            // Exactly one notification, and it describes the cause. Telling someone their booking
            // was "cancelled" when their card was declined explains nothing they can act on.
            assertThat(notification).get().extracting(BookingNotification::type)
                    .isEqualTo(NotificationType.PAYMENT_FAILED);
        }

        @Test
        void thereIsNoSecondNotificationBecauseOnlyOneTopicIsConsumed() {
            // The guarantee is structural rather than suppressed: payment-events is never read, so
            // no second message can exist for the same incident. It also could not carry a
            // recipient — Contact belongs to the Booking aggregate, not to payment-service.
            Optional<BookingNotification> notification =
                    translator.translate(event("CANCELLED", "PAYMENT_FAILED", "EMAIL"));

            assertThat(notification).isPresent();
            assertThat(notification.get().type()).isEqualTo(NotificationType.PAYMENT_FAILED);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void anExplicitTravellerCancellationIsReportedAsACancellation() {
            Optional<BookingNotification> notification =
                    translator.translate(event("CANCELLED", "TRAVELER_REQUESTED", "EMAIL"));

            assertThat(notification).get().extracting(BookingNotification::type)
                    .isEqualTo(NotificationType.BOOKING_CANCELLED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"HOLD_EXPIRED", "TRIP_CANCELLED", "PROVIDER_CONFIRMATION_FAILED"})
        void cancellationsTheTravellerDidNotCauseAreStillReportedToThem(String reason) {
            // Each is a real cancellation nobody else tells them about. Silence would be the worse
            // failure — particularly TRIP_CANCELLED, where they would otherwise arrive to board.
            assertThat(translator.translate(event("CANCELLED", reason, "EMAIL"))).get()
                    .extracting(BookingNotification::type).isEqualTo(NotificationType.BOOKING_CANCELLED);
        }
    }

    @Nested
    class ChannelSelection {

        @Test
        void anEmailPreferenceSendsEmailOnly() {
            assertThat(translator.translate(event("CONFIRMED", null, "EMAIL"))).get()
                    .extracting(BookingNotification::channel).isEqualTo(NotificationChannel.EMAIL);
        }

        @Test
        void anSmsPreferenceSendsSmsOnly() {
            // The traveller was asked which they wanted; sending both ignores the answer.
            assertThat(translator.translate(event("CONFIRMED", null, "SMS"))).get()
                    .extracting(BookingNotification::channel).isEqualTo(NotificationChannel.SMS);
        }

        @Test
        void anAbsentPreferenceDefaultsToEmail() {
            assertThat(translator.translate(event("CONFIRMED", null, null))).get()
                    .extracting(BookingNotification::channel).isEqualTo(NotificationChannel.EMAIL);
        }

        @Test
        void fallsBackToEmailWhenSmsIsPreferredButNoNumberWasGiven() {
            BookingEventMessage message = new BookingEventMessage("CONFIRMED", UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), "CONFIRMED", null,
                    Instant.parse("2026-08-12T09:00:00Z"), UUID.randomUUID(), "RS-1234",
                    "traveller@example.com", "  ", "SMS", null, null, null);

            // Reaching them on the other channel beats recording a failure for a customer who is
            // perfectly contactable.
            assertThat(translator.translate(message)).get()
                    .extracting(BookingNotification::channel).isEqualTo(NotificationChannel.EMAIL);
        }

        @Test
        void producesNothingWhenTheBookingCarriesNoContactDetailsAtAll() {
            BookingEventMessage message = new BookingEventMessage("CONFIRMED", UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), "CONFIRMED", null,
                    Instant.parse("2026-08-12T09:00:00Z"), UUID.randomUUID(), "RS-1234",
                    null, null, "EMAIL", null, null, null);

            assertThat(translator.translate(message)).isEmpty();
        }
    }

    @Nested
    class Malformed {

        @Test
        void anEventWithNoIdIsDeclinedRatherThanSentWithoutAnIdempotencyKey() {
            BookingEventMessage message = new BookingEventMessage("CONFIRMED", UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), "CONFIRMED", null,
                    Instant.parse("2026-08-12T09:00:00Z"), null, "RS-1234",
                    "traveller@example.com", "+919876543210", "EMAIL", null, null, null);

            // Without an eventId there is nothing to de-duplicate on, so every redelivery would be
            // a fresh send. Not sending is the safer failure.
            assertThat(translator.translate(message)).isEmpty();
        }

        @Test
        void anUnrecognisedEventTypeIsIgnoredRatherThanGuessedAt() {
            assertThat(translator.translate(event("SOMETHING_NEW", null, "EMAIL"))).isEmpty();
        }
    }
}
