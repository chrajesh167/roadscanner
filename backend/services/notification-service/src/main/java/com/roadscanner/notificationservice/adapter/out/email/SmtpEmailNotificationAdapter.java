package com.roadscanner.notificationservice.adapter.out.email;

import com.roadscanner.notificationservice.config.NotificationProperties;
import com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.out.EmailNotificationPort;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sends via SMTP, using whatever server the environment configured.
 *
 * <p>Plain text through {@link SimpleMailMessage}: the three messages this service sends are short
 * and factual, and HTML would add a second body to keep in sync for no gain a traveler would notice.
 *
 * <p>The recipient comes from the event — that is, from what the customer typed at checkout — and
 * never from configuration. A configured recipient would send every traveler's booking confirmation
 * to one address, which is a data leak with a plausible-looking demo attached to it.
 *
 * <p>Only the {@code from} address is configuration, because it identifies the sender rather than
 * the customer.
 */
public class SmtpEmailNotificationAdapter implements EmailNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties.Email properties;

    public SmtpEmailNotificationAdapter(JavaMailSender mailSender, NotificationProperties.Email properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(Recipient recipient, NotificationMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(recipient.value());
        mail.setSubject(message.subject());
        mail.setText(message.body());

        try {
            mailSender.send(mail);
            // Masked, and no body: an email body carries the booking reference and the fare.
            log.info("Email accepted by SMTP server for {}", recipient.masked());
        } catch (MailException e) {
            // The cause is attached but the message is not — a mail server's rejection text can
            // echo the address back, and this string is written to the notification log.
            throw new NotificationDeliveryException("SMTP server rejected the message", e);
        }
    }
}
