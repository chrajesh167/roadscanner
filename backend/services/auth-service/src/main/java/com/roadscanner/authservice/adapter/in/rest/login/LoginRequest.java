package com.roadscanner.authservice.adapter.in.rest.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** See {@link com.roadscanner.authservice.adapter.in.rest.registration.RegisterRequest} for
 * the structural-validation rationale these constraints follow. */
public record LoginRequest(
        @NotBlank(message = "identifier is required")
        @Size(max = 255, message = "identifier must be at most 255 characters")
        String identifier,

        @NotBlank(message = "password is required")
        @Size(max = 128, message = "password must be at most 128 characters")
        String password,

        @Size(max = 255, message = "deviceLabel must be at most 255 characters")
        String deviceLabel
) {

    @Override
    public String toString() {
        return "LoginRequest[identifier=" + identifier + ", password=redacted]";
    }
}
