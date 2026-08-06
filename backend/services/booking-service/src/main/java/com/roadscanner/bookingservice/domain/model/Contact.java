package com.roadscanner.bookingservice.domain.model;

/**
 * Where the provider sends the ticket — one contact per booking, not per passenger, matching
 * {@code provider-integration-service}'s {@code ContactRequest} and FlixBus's checkout contract.
 *
 * <p>This service previously held no contact data at all, while the provider requires it and
 * rejects a checkout without it. A booking that cannot be delivered to anyone is not a booking, so
 * the field is mandatory rather than optional-with-a-fallback: substituting the traveller's account
 * email would put a ticket somewhere nobody agreed it should go.
 *
 * <p>{@code phone} is kept exactly as entered. FlixBus wants an E.164-style number with a country
 * code, and this service is in no position to guess which country a bare national number belongs
 * to — the form asks for the full number instead.
 */
public record Contact(String phone, String email, CommunicationPreference communicationPreference) {

    public Contact {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phone must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (communicationPreference == null) {
            communicationPreference = CommunicationPreference.EMAIL;
        }
        phone = phone.trim();
        email = email.trim();
    }

    /** The two values the provider accepts. Lower-cased on the wire by the adapter. */
    public enum CommunicationPreference {
        EMAIL,
        SMS;

        public static CommunicationPreference parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return EMAIL;
            }
            return switch (raw.trim().toLowerCase()) {
                case "sms" -> SMS;
                case "email" -> EMAIL;
                default -> throw new IllegalArgumentException(
                        "communicationPreference must be 'email' or 'sms', got: " + raw);
            };
        }

        public String wireValue() {
            return name().toLowerCase();
        }
    }
}
