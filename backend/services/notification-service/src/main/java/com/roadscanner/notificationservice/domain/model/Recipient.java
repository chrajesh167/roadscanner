package com.roadscanner.notificationservice.domain.model;

import java.util.Objects;

/**
 * Where a notification is being sent, and the only place that value is allowed to be rendered
 * for a log.
 *
 * <p>{@link #masked()} exists so no caller has to remember to mask. A recipient printed in full
 * puts a customer's email address or phone number into every log aggregator the platform ships to,
 * where it long outlives the booking it belonged to.
 */
public record Recipient(NotificationChannel channel, String value) {

    public Recipient {
        Objects.requireNonNull(channel, "channel must not be null");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("recipient value must not be blank");
        }
        value = value.trim();
    }

    /**
     * A form safe to log: enough to correlate a complaint with a row, never enough to contact
     * anyone or to identify them from the log alone.
     *
     * <p>Phone numbers keep their last four digits ({@code ***6789}) — the digits a person quotes
     * when asked which number they used. Email keeps the first character and the domain
     * ({@code a***@example.com}), because the domain is what distinguishes a delivery problem
     * affecting one provider from one affecting everybody.
     */
    public String masked() {
        return switch (channel) {
            case SMS -> maskPhone(value);
            case EMAIL -> maskEmail(value);
        };
    }

    private static String maskPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "***";
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Override
    public String toString() {
        // The record's generated toString would print the address in full, and it is exactly the
        // kind of thing that reaches a log through string interpolation nobody reviewed.
        return "Recipient[" + channel + ", " + masked() + "]";
    }
}
