package com.roadscanner.authservice.adapter.in.rest.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Structural validation only (docs/services/auth-service/validation-strategy.md): presence and
 * sane length, rejected before any use case runs. Identifier shape (email-or-phone) is the
 * {@code LoginIdentifier} value object's invariant; password complexity is the domain policy's.
 * {@code password} max length caps the input handed to BCrypt — an unbounded value would let a
 * caller turn the deliberately-slow hash into a load amplifier.
 */
public record RegisterRequest(
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
        return "RegisterRequest[identifier=" + identifier + ", password=redacted]";
    }
}
