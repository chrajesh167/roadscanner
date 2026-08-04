package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Where the provider sends the ticket — one contact per booking, not per passenger. */
record ContactRequest(
        @NotBlank @Schema(example = "+919876543210") String phone,
        @NotBlank @Email @Schema(example = "asha@example.com") String email,
        @Schema(allowableValues = {"email", "sms"}, example = "email") String communicationPreference) {

    ContactDetail toDomain() {
        return new ContactDetail(phone, email,
                ContactDetail.CommunicationPreference.parse(communicationPreference));
    }
}
