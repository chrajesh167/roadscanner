package com.roadscanner.notificationservice.application.usecase;

import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns a {@link BookingNotification} into words.
 *
 * <p>Plain text, composed in Java. A template engine would be a dependency, a build step and a set
 * of files to keep in sync with this vocabulary, for three messages whose entire content is known
 * at compile time.
 *
 * <p>The wording rules that matter:
 *
 * <ul>
 *   <li>A payment failure never uses confirmation language, and says explicitly that no seat is
 *       held. A traveler who skims "your RoadScanner booking" and stops reading must not come away
 *       believing they are travelling.</li>
 *   <li>Nothing is stated that this service cannot know. There is no route line, because
 *       booking-service does not carry origin and destination; an empty "→" would read as a bug.
 *       Fields that can legitimately be absent are omitted rather than printed blank.</li>
 *   <li>SMS bodies are short and carry no fare or contact details — an SMS is read on a lock screen,
 *       in front of whoever else is looking at it.</li>
 * </ul>
 */
class NotificationComposer {

    private static final DateTimeFormatter DEPARTURE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    NotificationMessage compose(BookingNotification notification, NotificationChannel channel) {
        return channel == NotificationChannel.SMS ? sms(notification) : email(notification);
    }

    private NotificationMessage email(BookingNotification notification) {
        String reference = notification.bookingReferenceIfPresent().orElse(null);
        return switch (notification.type()) {
            case BOOKING_CONFIRMED -> new NotificationMessage(
                    "Your RoadScanner booking is confirmed"
                            + (reference == null ? "" : " (" + reference + ")"),
                    join(
                            "Your booking is confirmed.",
                            "",
                            line("Booking reference", reference),
                            departureLine(notification),
                            fareLine(notification, "Fare paid"),
                            "",
                            "Please arrive at your boarding point at least 15 minutes before departure.",
                            "",
                            "— RoadScanner"));
            case BOOKING_CANCELLED -> new NotificationMessage(
                    "Your RoadScanner booking has been cancelled"
                            + (reference == null ? "" : " (" + reference + ")"),
                    join(
                            "Your booking has been cancelled and your seat has been released.",
                            "",
                            line("Booking reference", reference),
                            departureLine(notification),
                            fareLine(notification, "Amount paid"),
                            "",
                            // Deliberately does not promise a refund or a timescale. This service
                            // does not observe refunds, and inventing one would be a commitment
                            // nobody here can keep.
                            "If a payment was taken for this booking, any refund due is processed "
                                    + "separately and will be confirmed on its own.",
                            "",
                            "— RoadScanner"));
            case PAYMENT_FAILED -> new NotificationMessage(
                    "Payment failed — your RoadScanner booking was not completed",
                    join(
                            "We could not take payment for your booking, so it was not completed "
                                    + "and no seat is being held for you.",
                            "",
                            line("Booking reference", reference),
                            departureLine(notification),
                            fareLine(notification, "Amount attempted"),
                            "",
                            "You have not been charged. To travel on this service, please search "
                                    + "again and book with a different payment method.",
                            "",
                            "— RoadScanner"));
        };
    }

    private NotificationMessage sms(BookingNotification notification) {
        String reference = notification.bookingReferenceIfPresent().map(r -> " Ref " + r).orElse("");
        String body = switch (notification.type()) {
            case BOOKING_CONFIRMED -> "RoadScanner: your booking is confirmed." + reference
                    + notification.departureTimeIfPresent()
                    .map(departure -> " Departs " + DEPARTURE_FORMAT.format(departure) + ".").orElse("");
            case BOOKING_CANCELLED -> "RoadScanner: your booking has been cancelled and your seat released."
                    + reference;
            case PAYMENT_FAILED -> "RoadScanner: payment failed, so your booking was not completed "
                    + "and no seat is held. You have not been charged." + reference;
        };
        // Subject is carried for shape only; no SMS adapter reads it.
        return new NotificationMessage("RoadScanner", body);
    }

    private String departureLine(BookingNotification notification) {
        return notification.departureTimeIfPresent()
                .map(departure -> "Departure: " + DEPARTURE_FORMAT.format(departure) + " IST")
                .orElse(null);
    }

    private String fareLine(BookingNotification notification, String label) {
        return notification.hasFare()
                ? label + ": " + notification.fareCurrency() + " " + notification.fareAmount().toPlainString()
                : null;
    }

    private String line(String label, String value) {
        return value == null ? null : label + ": " + value;
    }

    /** Drops absent lines rather than printing labels with nothing after them. */
    private String join(String... lines) {
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            body.append(line).append('\n');
        }
        return body.toString().strip();
    }
}
