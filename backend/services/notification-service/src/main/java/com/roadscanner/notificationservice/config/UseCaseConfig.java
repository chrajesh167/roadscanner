package com.roadscanner.notificationservice.config;

import com.roadscanner.notificationservice.application.usecase.SendBookingNotificationService;
import com.roadscanner.notificationservice.domain.port.in.SendBookingNotification;
import com.roadscanner.notificationservice.domain.port.out.EmailNotificationPort;
import com.roadscanner.notificationservice.domain.port.out.NotificationLogRepository;
import com.roadscanner.notificationservice.domain.port.out.SmsNotificationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Explicit bean wiring for every application-layer use case — matching every other service in
 * this codebase's identical {@code UseCaseConfig} convention: plain constructors, no Spring
 * stereotype annotations on the application classes themselves. */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SendBookingNotification sendBookingNotification(NotificationLogRepository notificationLog,
                                                            EmailNotificationPort emailPort,
                                                            SmsNotificationPort smsPort, Clock clock) {
        return new SendBookingNotificationService(notificationLog, emailPort, smsPort, clock);
    }
}
