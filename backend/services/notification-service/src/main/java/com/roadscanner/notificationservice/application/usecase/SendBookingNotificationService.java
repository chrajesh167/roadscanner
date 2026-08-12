package com.roadscanner.notificationservice.application.usecase;

import com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException;
import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;
import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.port.in.SendBookingNotification;
import com.roadscanner.notificationservice.domain.port.out.EmailNotificationPort;
import com.roadscanner.notificationservice.domain.port.out.NotificationLogRepository;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;
import com.roadscanner.notificationservice.domain.port.out.SmsNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Implements {@link SendBookingNotification}: claim, compose, send, record.
 *
 * <h2>Claim before send</h2>
 * The {@code PENDING} row is written <em>before</em> the channel is called, and the write is what
 * enforces uniqueness on {@code (eventId, channel)}. Checking first and sending after would leave a
 * window in which a redelivery of the same event passes the check on another instance and sends a
 * second copy. Losing the race means someone else has this event; that is a success, not an error.
 *
 * <p>The cost of claiming first is that a process which dies mid-send leaves a {@code PENDING} row
 * and the customer may get nothing. That is the right way round: a missing notification is
 * recoverable and visible in the log, whereas a duplicate confirmation has already reached someone.
 *
 * <h2>Nothing here can fail a booking</h2>
 * Every delivery failure is caught, recorded, and swallowed. The booking it describes was durable in
 * another service's database long before this ran, and this service has no way to change it and no
 * business doing so. Rethrowing would fail the Kafka listener, which redelivers, which re-sends —
 * turning one bad SMTP response into a loop against a customer's inbox.
 */
public class SendBookingNotificationService implements SendBookingNotification {

    private static final Logger log = LoggerFactory.getLogger(SendBookingNotificationService.class);

    private final NotificationLogRepository notificationLog;
    private final EmailNotificationPort emailPort;
    private final SmsNotificationPort smsPort;
    private final NotificationComposer composer = new NotificationComposer();
    private final Clock clock;

    public SendBookingNotificationService(NotificationLogRepository notificationLog, EmailNotificationPort emailPort,
                                           SmsNotificationPort smsPort, Clock clock) {
        this.notificationLog = notificationLog;
        this.emailPort = emailPort;
        this.smsPort = smsPort;
        this.clock = clock;
    }

    @Override
    public Optional<NotificationRecord> send(Command command) {
        BookingNotification notification = command.notification();
        Instant now = clock.instant();

        Optional<NotificationRecord> claimed = notificationLog.claim(NotificationRecord.pending(
                notification.eventId(), notification.bookingId(), notification.type(),
                notification.recipient(), now));

        if (claimed.isEmpty()) {
            log.debug("Notification for event {} on {} already handled — not sending again",
                    notification.eventId(), notification.channel());
            return Optional.empty();
        }

        NotificationRecord record = claimed.get();
        NotificationMessage message = composer.compose(notification, notification.channel());

        try {
            NotificationStatus achieved = switch (notification.channel()) {
                case EMAIL -> {
                    emailPort.send(notification.recipient(), message);
                    yield NotificationStatus.SENT;
                }
                // The SMS port reports what it achieved, because a stand-in adapter must not be
                // recorded as a carrier delivery.
                case SMS -> smsPort.send(notification.recipient(), message);
            };
            record.markDelivered(achieved, clock.instant());
            log.info("Notification {} for booking {} on {} to {}: {}", notification.type(),
                    notification.bookingId(), notification.channel(), notification.recipient().masked(), achieved);
        } catch (NotificationDeliveryException e) {
            record.markFailed(e.getMessage(), clock.instant());
            // Recorded and logged, never rethrown — see this class's Javadoc.
            log.warn("Notification {} for booking {} on {} to {} failed", notification.type(),
                    notification.bookingId(), notification.channel(), notification.recipient().masked(), e);
        } catch (RuntimeException e) {
            // An adapter that threw something other than the port's declared failure is a bug in
            // that adapter, but it still must not reach the listener and cause redelivery.
            record.markFailed("unexpected: " + e.getClass().getSimpleName(), clock.instant());
            log.error("Notification {} for booking {} on {} failed unexpectedly", notification.type(),
                    notification.bookingId(), notification.channel(), e);
        }

        return Optional.of(notificationLog.save(record));
    }
}
