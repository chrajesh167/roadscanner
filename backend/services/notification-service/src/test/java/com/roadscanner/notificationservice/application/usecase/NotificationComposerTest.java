package com.roadscanner.notificationservice.application.usecase;

import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationType;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the traveller actually reads.
 *
 * <p>The assertion that matters most is that a payment failure contains no confirmation language.
 * Someone who skims the first line and stops must not come away believing they are travelling.
 */
class NotificationComposerTest {

    private final NotificationComposer composer = new NotificationComposer();

    private BookingNotification notification(NotificationType type, String reference, boolean withDetails) {
        return new BookingNotification(UUID.randomUUID(), UUID.randomUUID(), type,
                new Recipient(NotificationChannel.EMAIL, "traveller@example.com"), reference,
                withDetails ? Instant.parse("2026-08-13T20:00:00Z") : null,
                withDetails ? new BigDecimal("899.00") : null, withDetails ? "INR" : null,
                Instant.parse("2026-08-12T09:00:00Z"));
    }

    @Test
    void aConfirmationStatesTheBookingReferenceDepartureAndFare() {
        NotificationMessage message = composer.compose(
                notification(NotificationType.BOOKING_CONFIRMED, "RS-1234", true), NotificationChannel.EMAIL);

        assertThat(message.subject()).contains("confirmed").contains("RS-1234");
        assertThat(message.body())
                .contains("Your booking is confirmed.")
                .contains("RS-1234")
                .contains("INR 899.00")
                // Rendered in IST, which is the timezone every traveller on this platform is in.
                // 20:00 UTC is 01:30 the following morning locally — the date shifts, and showing
                // the UTC date would have someone turn up on the wrong day.
                .contains("14 Aug 2026, 01:30 IST");
    }

    @Test
    void aPaymentFailureNeverUsesConfirmationLanguageAndSaysNoSeatIsHeld() {
        NotificationMessage message = composer.compose(
                notification(NotificationType.PAYMENT_FAILED, "RS-1234", true), NotificationChannel.EMAIL);

        assertThat(message.subject()).contains("Payment failed").doesNotContainIgnoringCase("confirmed");
        assertThat(message.body())
                .contains("could not take payment")
                .contains("no seat is being held")
                .contains("You have not been charged")
                .doesNotContain("is confirmed");
    }

    @Test
    void aCancellationSaysTheSeatWasReleasedWithoutPromisingARefund() {
        NotificationMessage message = composer.compose(
                notification(NotificationType.BOOKING_CANCELLED, "RS-1234", true), NotificationChannel.EMAIL);

        assertThat(message.body()).contains("cancelled").contains("seat has been released");
        // This service never observes a refund, so it must not commit to one or to a timescale.
        assertThat(message.body()).doesNotContain("will be refunded").doesNotContain("business days");
    }

    @Test
    void absentFieldsAreOmittedRatherThanPrintedAsEmptyLabels() {
        NotificationMessage message = composer.compose(
                notification(NotificationType.BOOKING_CANCELLED, null, false), NotificationChannel.EMAIL);

        // A booking cancelled before the provider confirmed never had a reference, and this service
        // never knows the route — a dangling "Booking reference:" or an empty arrow reads as a bug.
        assertThat(message.body()).doesNotContain("Booking reference:").doesNotContain("Departure:");
        assertThat(message.subject()).isEqualTo("Your RoadScanner booking has been cancelled");
    }

    @Test
    void smsBodiesStayShortAndCarryNoFare() {
        NotificationMessage message = composer.compose(
                notification(NotificationType.BOOKING_CONFIRMED, "RS-1234", true), NotificationChannel.SMS);

        // Read on a lock screen, in front of whoever else is looking at it.
        assertThat(message.body()).hasSizeLessThan(160).contains("RS-1234").doesNotContain("899");
    }

    @Test
    void anSmsPaymentFailureIsAsUnambiguousAsTheEmail() {
        NotificationMessage message = composer.compose(
                notification(NotificationType.PAYMENT_FAILED, "RS-1234", true), NotificationChannel.SMS);

        assertThat(message.body())
                .contains("payment failed")
                .contains("no seat is held")
                .doesNotContain("confirmed");
    }
}
