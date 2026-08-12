package com.roadscanner.notificationservice.config;

import com.roadscanner.notificationservice.adapter.out.email.LoggingEmailNotificationAdapter;
import com.roadscanner.notificationservice.adapter.out.email.SmtpEmailNotificationAdapter;
import com.roadscanner.notificationservice.adapter.out.sms.MockSmsNotificationAdapter;
import com.roadscanner.notificationservice.domain.port.out.EmailNotificationPort;
import com.roadscanner.notificationservice.domain.port.out.SmsNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the channel adapters from configuration.
 *
 * <p>The service starts either way. Refusing to start without SMTP would stop the whole platform
 * being runnable by a developer who has no mail server, and there is nothing unsafe about a
 * notification service that records honest failures. What it will not do is silently substitute a
 * fake sender for a real one — see {@link LoggingEmailNotificationAdapter}.
 */
@Configuration
public class NotificationChannelConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelConfig.class);

    /**
     * {@link ObjectProvider} rather than a direct dependency: Spring Boot only creates a
     * {@link JavaMailSender} when {@code spring.mail.host} is set, so requiring one outright would
     * stop this service starting on any machine without a mail server — which is most of them, and
     * exactly the situation the fallback adapter exists to handle.
     */
    @Bean
    public EmailNotificationPort emailNotificationPort(ObjectProvider<JavaMailSender> mailSender,
                                                        NotificationProperties properties) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!properties.email().isConfigured() || sender == null) {
            log.warn("No SMTP host configured (NOTIFICATION_EMAIL_HOST unset) — email notifications "
                    + "will be recorded as FAILED rather than sent. This is expected for a local run "
                    + "without a mail server.");
            return new LoggingEmailNotificationAdapter();
        }
        log.info("Email notifications will be sent via SMTP from {}", properties.email().from());
        return new SmtpEmailNotificationAdapter(sender, properties.email());
    }

    /**
     * No real provider adapter exists yet: every SMS vendor requires account verification before it
     * will deliver, which is an onboarding task rather than a coding one. The port is here so adding
     * one is a new bean and nothing else.
     */
    @Bean
    public SmsNotificationPort smsNotificationPort() {
        log.info("SMS notifications use the demo adapter — messages are recorded, never transmitted");
        return new MockSmsNotificationAdapter();
    }
}
