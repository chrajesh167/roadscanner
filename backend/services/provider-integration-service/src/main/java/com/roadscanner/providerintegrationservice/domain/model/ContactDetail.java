package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Who the provider contacts about a booking — one contact per booking, not per passenger.
 *
 * <p>Separate from {@link PassengerDetail} because it is booking-scoped: a family of four travels
 * on four passenger records but one phone number and one mailbox receive the ticket.
 *
 * <p>Provider-neutral. Every provider that issues a ticket needs somewhere to send it, so this is
 * not a FlixBus concept even though FlixBus is the first provider to require it.
 */
public record ContactDetail(String phone, String email, CommunicationPreference communicationPreference) {

    public ContactDetail {
        phone = requireNonBlank(phone, "phone");
        email = requireNonBlank(email, "email");
        Objects.requireNonNull(communicationPreference, "communicationPreference must not be null");

        if (!email.contains("@")) {
            throw new IllegalArgumentException("email must be an email address");
        }
    }

    /**
     * The phone in E.164 form.
     *
     * <p>Normalized here rather than at the adapter so every provider gets the same value: a
     * number that reaches one provider but not another is a support ticket nobody can reproduce.
     * A number that already carries a {@code +} is left alone — prefixing it again produces
     * {@code ++91...}, which is silently accepted by some APIs and then never dialled.
     *
     * @param defaultCallingCode e.g. {@code "+91"}, applied only to a number that has no prefix
     */
    public String phoneInInternationalFormat(String defaultCallingCode) {
        requireNonBlank(defaultCallingCode, "defaultCallingCode");
        String compact = phone.replaceAll("[\\s-]", "");
        return compact.startsWith("+") ? compact : defaultCallingCode + compact;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    /** How the traveller wants to hear about the booking. */
    public enum CommunicationPreference {
        EMAIL,
        SMS;

        public static CommunicationPreference parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return EMAIL;
            }
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        }

        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
