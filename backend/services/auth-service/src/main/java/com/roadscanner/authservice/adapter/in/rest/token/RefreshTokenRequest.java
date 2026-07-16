package com.roadscanner.authservice.adapter.in.rest.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body for both refresh and logout — each presents the raw refresh token as its credential. */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken is required")
        @Size(max = 512, message = "refreshToken must be at most 512 characters")
        String refreshToken
) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=redacted]";
    }
}
