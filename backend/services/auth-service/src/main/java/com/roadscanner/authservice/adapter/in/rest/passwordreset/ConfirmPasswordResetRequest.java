package com.roadscanner.authservice.adapter.in.rest.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordResetRequest(
        @NotBlank(message = "token is required")
        @Size(max = 512, message = "token must be at most 512 characters")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(max = 128, message = "newPassword must be at most 128 characters")
        String newPassword
) {

    @Override
    public String toString() {
        return "ConfirmPasswordResetRequest[token=redacted, newPassword=redacted]";
    }
}
