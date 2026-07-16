package com.roadscanner.authservice.adapter.in.rest.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPasswordResetRequest(
        @NotBlank(message = "identifier is required")
        @Size(max = 255, message = "identifier must be at most 255 characters")
        String identifier
) {
}
