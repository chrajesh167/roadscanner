package com.roadscanner.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RoadScanner's notification service: consumes booking lifecycle events and tells the traveler
 * what happened, by email or SMS.
 *
 * <p>It owns nothing but its own notification log. It never writes to a booking, never calls
 * another service, and cannot change the outcome of anything it reports on — a message that fails
 * to send leaves the booking exactly as it was.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
