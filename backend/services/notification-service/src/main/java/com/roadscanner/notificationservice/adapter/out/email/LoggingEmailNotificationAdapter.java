package com.roadscanner.notificationservice.adapter.out.email;

import com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.out.EmailNotificationPort;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The email adapter used when no SMTP host is configured.
 *
 * <p>It <strong>fails</strong> rather than pretending. The alternative — logging the message and
 * reporting success — would fill the notification log with {@code SENT} rows for mail that was
 * never sent, and the first time anyone trusted that log they would be wrong about something a
 * customer is waiting for.
 *
 * <p>Recording {@code FAILED} with "no SMTP host configured" is the honest outcome, and it is
 * visible in exactly the place an operator would look. The service still starts and still consumes
 * events, so a developer with no mail server can run the whole platform and see the notification
 * pipeline work end to end without a mail server quietly being simulated.
 */
public class LoggingEmailNotificationAdapter implements EmailNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailNotificationAdapter.class);

    @Override
    public void send(Recipient recipient, NotificationMessage message) {
        log.warn("No SMTP host configured — email to {} was not sent. Subject: {}",
                recipient.masked(), message.subject());
        throw new NotificationDeliveryException(
                "No SMTP host configured (set NOTIFICATION_EMAIL_HOST) — email was not sent");
    }
}
