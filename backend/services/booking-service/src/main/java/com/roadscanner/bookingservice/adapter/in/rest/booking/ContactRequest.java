package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.domain.model.Contact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Where the ticket is sent — one contact per booking, not per passenger.
 *
 * <p>Supplied at booking creation rather than at hold time because that is when the provider needs
 * it: the seat block cares who occupies a seat, the checkout cares where the ticket goes.
 */
public record ContactRequest(
        @NotBlank @Schema(description = "Full number including country code", example = "+919876543210")
        String phone,
        @NotBlank @Email @Schema(example = "asha@example.com") String email,
        @Schema(allowableValues = {"email", "sms"}, example = "email") String communicationPreference) {

    public Contact toDomain() {
        return new Contact(phone, email, Contact.CommunicationPreference.parse(communicationPreference));
    }
}
