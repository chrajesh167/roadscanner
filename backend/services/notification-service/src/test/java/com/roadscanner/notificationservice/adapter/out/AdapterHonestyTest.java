package com.roadscanner.notificationservice.adapter.out;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roadscanner.notificationservice.adapter.out.email.LoggingEmailNotificationAdapter;
import com.roadscanner.notificationservice.adapter.out.email.SmtpEmailNotificationAdapter;
import com.roadscanner.notificationservice.adapter.out.sms.MockSmsNotificationAdapter;
import com.roadscanner.notificationservice.config.NotificationProperties;
import com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException;
import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * What the channel adapters are allowed to claim, and what they are allowed to log.
 *
 * <p>These are the assertions that keep the notification log trustworthy. A stand-in that reported
 * {@code SENT}, or an adapter that logged a customer's phone number, would both be invisible in
 * ordinary use and wrong in exactly the situation someone later relies on.
 */
class AdapterHonestyTest {

    private static final Recipient PHONE = new Recipient(NotificationChannel.SMS, "+919876543210");
    private static final Recipient EMAIL = new Recipient(NotificationChannel.EMAIL, "traveller@example.com");
    private static final NotificationMessage MESSAGE =
            new NotificationMessage("RoadScanner", "Your booking is confirmed. Ref RS-1234");

    private Logger root;
    private ListAppender<ILoggingEvent> captured;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(captured);
    }

    @Test
    void theMockSmsAdapterNeverClaimsACarrierDeliveredAnything() {
        NotificationStatus status = new MockSmsNotificationAdapter().send(PHONE, MESSAGE);

        // DEMO_RECORDED, never SENT. This is the whole reason the status exists.
        assertThat(status).isEqualTo(NotificationStatus.DEMO_RECORDED);
        assertThat(captured.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("nothing was transmitted"));
    }

    @Test
    void theMockSmsAdapterLogsNeitherTheNumberNorTheMessageBody() {
        new MockSmsNotificationAdapter().send(PHONE, MESSAGE);

        assertThat(captured.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain("9876543210")
                .doesNotContain("RS-1234"));
    }

    @Test
    void theUnconfiguredEmailAdapterFailsRatherThanPretendingToSend() {
        // Reporting success here would fill the log with SENT rows for mail nobody sent, and the
        // first person to trust that log would be wrong about something a customer is waiting for.
        assertThatThrownBy(() -> new LoggingEmailNotificationAdapter().send(EMAIL, MESSAGE))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("NOTIFICATION_EMAIL_HOST");

        assertThat(captured.list).allSatisfy(event ->
                assertThat(event.getFormattedMessage()).doesNotContain("traveller@example.com"));
    }

    @Test
    void theSmtpAdapterSendsToTheRecipientFromTheBookingNotToAConfiguredAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailNotificationAdapter adapter = new SmtpEmailNotificationAdapter(mailSender,
                new NotificationProperties.Email("smtp.example.com", "RoadScanner <no-reply@roadscanner.test>"));

        adapter.send(EMAIL, MESSAGE);

        // A configured recipient would send every traveller's booking to one inbox — a data leak
        // with a convincing-looking demo attached to it.
        SimpleMailMessage expected = new SimpleMailMessage();
        expected.setFrom("RoadScanner <no-reply@roadscanner.test>");
        expected.setTo("traveller@example.com");
        expected.setSubject("RoadScanner");
        expected.setText("Your booking is confirmed. Ref RS-1234");
        verify(mailSender).send(expected);
    }

    @Test
    void theSmtpAdapterReportsAFailureWithoutEchoingTheAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 rejected: traveller@example.com unknown"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpEmailNotificationAdapter adapter = new SmtpEmailNotificationAdapter(mailSender,
                new NotificationProperties.Email("smtp.example.com", "RoadScanner <no-reply@roadscanner.test>"));

        // The failure reason is written to the notification log, so it must not carry the address
        // that the mail server happened to echo back.
        assertThatThrownBy(() -> adapter.send(EMAIL, MESSAGE))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessage("SMTP server rejected the message");
    }
}
