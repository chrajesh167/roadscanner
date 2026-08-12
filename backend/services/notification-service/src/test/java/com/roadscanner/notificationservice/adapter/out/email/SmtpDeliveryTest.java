package com.roadscanner.notificationservice.adapter.out.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roadscanner.notificationservice.application.usecase.SendBookingNotificationService;
import com.roadscanner.notificationservice.config.NotificationChannelConfig;
import com.roadscanner.notificationservice.config.NotificationProperties;
import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;
import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.NotificationType;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.in.SendBookingNotification;
import com.roadscanner.notificationservice.domain.port.out.EmailNotificationPort;
import com.roadscanner.notificationservice.testsupport.InMemoryNotificationLogRepository;
import com.roadscanner.notificationservice.testsupport.RecordingChannels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The real SMTP adapter wired into the real application service, against a mocked
 * {@link JavaMailSender}.
 *
 * <p>Distinct from the tests that use a recording channel double: those prove the service's rules,
 * this proves the rule that spans the two — that {@code SENT} is written only once
 * {@code JavaMailSender} has actually returned. With the two halves tested separately, an adapter
 * that reported success before sending would satisfy both and still be wrong.
 *
 * <p>Nothing here touches a network. A test that needed Gmail would fail on an aeroplane, in CI,
 * and whenever an App Password was rotated.
 */
class SmtpDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-08-12T09:00:00Z");
    private static final String FROM = "RoadScanner <no-reply@roadscanner.test>";
    /** Not a real credential — a distinctive string, so a leak into a log is unmistakable. */
    private static final String APP_PASSWORD = "abcdefghijklmnop";

    private JavaMailSender mailSender;
    private InMemoryNotificationLogRepository notificationLog;
    private SendBookingNotification service;
    private Logger root;
    private ListAppender<ILoggingEvent> captured;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        notificationLog = new InMemoryNotificationLogRepository();
        service = new SendBookingNotificationService(notificationLog,
                new SmtpEmailNotificationAdapter(mailSender, new NotificationProperties.Email("smtp.gmail.com", FROM)),
                new RecordingChannels.Sms(), Clock.fixed(NOW, ZoneOffset.UTC));

        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(captured);
    }

    private BookingNotification confirmation() {
        return new BookingNotification(UUID.randomUUID(), UUID.randomUUID(), NotificationType.BOOKING_CONFIRMED,
                new Recipient(NotificationChannel.EMAIL, "traveller@example.com"), "RS-1234",
                Instant.parse("2026-08-13T20:00:00Z"), new BigDecimal("899.00"), "INR", NOW);
    }

    private Optional<NotificationRecord> send(BookingNotification notification) {
        return service.send(new SendBookingNotification.Command(notification));
    }

    @Test
    void aConfiguredSmtpServerIsActuallyInvokedAndTheNotificationRecordedSent() {
        Optional<NotificationRecord> record = send(confirmation());

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(record).get().satisfies(written -> {
            assertThat(written.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(written.sentAt()).contains(NOW);
        });
    }

    @Test
    void nothingIsRecordedSentWhenTheMailServerRejectsTheMessage() {
        doThrow(new MailSendException("535 authentication failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        Optional<NotificationRecord> record = send(confirmation());

        // The claim is written before the send, so the row exists either way — what must never
        // happen is that it says SENT for mail the server refused.
        assertThat(record).get().satisfies(written -> {
            assertThat(written.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(written.failureReason()).contains("SMTP server rejected the message");
        });
    }

    @Test
    void theMessageCarriesTheConfiguredSenderAndTheRecipientFromTheBooking() {
        send(confirmation());

        SimpleMailMessage expected = new SimpleMailMessage();
        expected.setFrom(FROM);
        // From configuration; to, from the booking's contact details. Never the other way round.
        expected.setTo("traveller@example.com");
        expected.setSubject("Your RoadScanner booking is confirmed (RS-1234)");
        expected.setText("""
                Your booking is confirmed.

                Booking reference: RS-1234
                Departure: 14 Aug 2026, 01:30 IST
                Fare paid: INR 899.00

                Please arrive at your boarding point at least 15 minutes before departure.

                — RoadScanner""");
        verify(mailSender).send(expected);
    }

    @Test
    void aRedeliveredEventSendsOnlyOneEmail() {
        BookingNotification notification = confirmation();

        send(notification);
        send(notification);

        // Idempotency has to hold at the SMTP boundary specifically: this is the point where a
        // duplicate stops being a database row and becomes a second message in someone's inbox.
        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(notificationLog.all()).hasSize(1);
    }

    @Test
    void noSmtpCredentialEverReachesALog() {
        doThrow(new MailSendException("535 authentication failed for " + APP_PASSWORD))
                .when(mailSender).send(any(SimpleMailMessage.class));

        Optional<NotificationRecord> record = send(confirmation());

        // A mail server can echo material back in its rejection text, and that text is both logged
        // and stored. Neither may carry it.
        assertThat(captured.list).allSatisfy(event ->
                assertThat(event.getFormattedMessage()).doesNotContain(APP_PASSWORD));
        assertThat(record).get().extracting(written -> written.failureReason().orElseThrow())
                .asString().doesNotContain(APP_PASSWORD);
    }

    @Test
    void theUnconfiguredFallbackIsChosenWhenNoHostIsSetAndNeverSendsAnything() {
        EmailNotificationPort port = new NotificationChannelConfig().emailNotificationPort(
                providerOf(mailSender), new NotificationProperties(
                        new NotificationProperties.Kafka("booking-events"),
                        new NotificationProperties.Email("", FROM)));

        assertThat(port).isInstanceOf(LoggingEmailNotificationAdapter.class);
    }

    @Test
    void theSmtpAdapterIsChosenAsSoonAsAHostIsConfigured() {
        EmailNotificationPort port = new NotificationChannelConfig().emailNotificationPort(
                providerOf(mailSender), new NotificationProperties(
                        new NotificationProperties.Kafka("booking-events"),
                        new NotificationProperties.Email("smtp.gmail.com", FROM)));

        assertThat(port).isInstanceOf(SmtpEmailNotificationAdapter.class);
    }

    @Test
    void aConfiguredHostWithNoMailSenderBeanStillFallsBackRatherThanFailingToStart() {
        // Spring only creates a JavaMailSender when spring.mail.host is set. If the two settings
        // ever disagree, the service must still start — it consumes events regardless of whether it
        // can send mail.
        EmailNotificationPort port = new NotificationChannelConfig().emailNotificationPort(
                providerOf(null), new NotificationProperties(
                        new NotificationProperties.Kafka("booking-events"),
                        new NotificationProperties.Email("smtp.gmail.com", FROM)));

        assertThat(port).isInstanceOf(LoggingEmailNotificationAdapter.class);
    }

    /**
     * Hand-rolled rather than mocked: Byte Buddy cannot instrument {@code ObjectProvider} on this
     * JDK, and the only behaviour the configuration reads is {@code getIfAvailable()}.
     */
    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        return new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject() {
                if (sender == null) {
                    throw new NoSuchBeanDefinitionException(JavaMailSender.class);
                }
                return sender;
            }

            @Override
            public JavaMailSender getObject(Object... args) {
                return getObject();
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return sender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return sender;
            }

            @Override
            public Iterator<JavaMailSender> iterator() {
                return sender == null ? Collections.emptyIterator() : List.of(sender).iterator();
            }
        };
    }

    @Test
    void theFallbackAdapterNeverInvokesAMailSender() {
        SendBookingNotification fallbackService = new SendBookingNotificationService(
                new InMemoryNotificationLogRepository(), new LoggingEmailNotificationAdapter(),
                new RecordingChannels.Sms(), Clock.fixed(NOW, ZoneOffset.UTC));

        Optional<NotificationRecord> record =
                fallbackService.send(new SendBookingNotification.Command(confirmation()));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertThat(record).get().extracting(NotificationRecord::status).isEqualTo(NotificationStatus.FAILED);
    }
}
