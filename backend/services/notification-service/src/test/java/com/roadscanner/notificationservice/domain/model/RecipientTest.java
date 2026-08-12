package com.roadscanner.notificationservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one place a recipient may be rendered for a log.
 *
 * <p>A phone number or email address printed in full reaches every log aggregator the platform
 * ships to and long outlives the booking it belonged to, so the guarantee worth pinning is that
 * neither the masked form nor {@code toString} ever contains the original.
 */
class RecipientTest {

    @Test
    void maskingAPhoneKeepsOnlyTheLastFourDigits() {
        Recipient recipient = new Recipient(NotificationChannel.SMS, "+91 98765 43210");

        assertThat(recipient.masked()).isEqualTo("***3210");
        assertThat(recipient.masked()).doesNotContain("98765");
    }

    @Test
    void maskingAnEmailKeepsOneCharacterAndTheDomain() {
        // The domain stays because it separates "one provider is bouncing us" from "everything is
        // bouncing", which is the first thing anyone asks.
        Recipient recipient = new Recipient(NotificationChannel.EMAIL, "traveller@example.com");

        assertThat(recipient.masked()).isEqualTo("t***@example.com");
        assertThat(recipient.masked()).doesNotContain("traveller");
    }

    @Test
    void toStringNeverRendersTheAddressInFull() {
        // The record's generated toString would, and string interpolation into a log message is
        // exactly how that reaches production unnoticed.
        Recipient email = new Recipient(NotificationChannel.EMAIL, "traveller@example.com");
        Recipient phone = new Recipient(NotificationChannel.SMS, "+919876543210");

        assertThat(email.toString()).doesNotContain("traveller@example.com");
        assertThat(phone.toString()).doesNotContain("9876543210");
    }

    @Test
    void aShortOrUnparseableValueMasksToNothingRatherThanLeakingWhatIsThere() {
        assertThat(new Recipient(NotificationChannel.SMS, "12").masked()).isEqualTo("***");
        assertThat(new Recipient(NotificationChannel.EMAIL, "not-an-email").masked()).isEqualTo("***");
    }
}
