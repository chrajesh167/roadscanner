package com.roadscanner.notificationservice.testsupport;

import com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException;
import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.out.EmailNotificationPort;
import com.roadscanner.notificationservice.domain.port.out.NotificationMessage;
import com.roadscanner.notificationservice.domain.port.out.SmsNotificationPort;

import java.util.ArrayList;
import java.util.List;

/** Channel doubles that record what they were asked to send, and can be told to fail. */
public final class RecordingChannels {

    public record Sent(Recipient recipient, NotificationMessage message) {
    }

    public static final class Email implements EmailNotificationPort {

        public final List<Sent> sent = new ArrayList<>();
        private RuntimeException failure;

        public void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void send(Recipient recipient, NotificationMessage message) {
            if (failure != null) {
                throw failure;
            }
            sent.add(new Sent(recipient, message));
        }
    }

    public static final class Sms implements SmsNotificationPort {

        public final List<Sent> sent = new ArrayList<>();
        private NotificationStatus outcome = NotificationStatus.DEMO_RECORDED;
        private NotificationDeliveryException failure;

        public void reportsOutcome(NotificationStatus outcome) {
            this.outcome = outcome;
        }

        public void failWith(NotificationDeliveryException failure) {
            this.failure = failure;
        }

        @Override
        public NotificationStatus send(Recipient recipient, NotificationMessage message) {
            if (failure != null) {
                throw failure;
            }
            sent.add(new Sent(recipient, message));
            return outcome;
        }
    }

    private RecordingChannels() {
    }
}
