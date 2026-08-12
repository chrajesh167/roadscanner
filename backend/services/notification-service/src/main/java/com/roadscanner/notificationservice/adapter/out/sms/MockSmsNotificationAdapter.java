package com.roadscanner.notificationservice.adapter.out.sms;

import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;
import com.roadscanner.notificationservice.domain.port.out.SmsNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SMS adapter used when no SMS provider is configured — which is the local and demo default,
 * because every real provider requires account verification before it will deliver anything.
 *
 * <p>It returns {@link NotificationStatus#DEMO_RECORDED}, never {@code SENT}. That distinction is
 * the entire point of this class: the notification log is the record of what reached a customer,
 * and a stand-in with no carrier behind it must not be able to write a row that says a message was
 * delivered. Anyone reading the log can tell at a glance which rows represent real traffic.
 *
 * <p>Unlike {@code LoggingEmailNotificationAdapter}, this does not throw. A missing SMS provider is
 * the expected state of this platform today, not a misconfiguration — recording it as a failure
 * would fill the log with alarms about a decision that was made deliberately.
 *
 * <p>The number is logged masked and the body is not logged at all. An SMS body carries the booking
 * reference, and the recipient is a phone number belonging to a real person.
 */
public class MockSmsNotificationAdapter implements SmsNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(MockSmsNotificationAdapter.class);

    @Override
    public NotificationStatus send(Recipient recipient, NotificationMessage message) {
        log.info("SMS recorded for demo (no provider configured, nothing was transmitted): to={}, {} chars",
                recipient.masked(), message.body().length());
        return NotificationStatus.DEMO_RECORDED;
    }
}
