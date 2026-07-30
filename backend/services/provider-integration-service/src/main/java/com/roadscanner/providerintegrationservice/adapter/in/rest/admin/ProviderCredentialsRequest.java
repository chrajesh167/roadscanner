package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Write shape for a provider's partner credentials.
 *
 * <p>A full replacement, not a patch: a half-updated credential set authenticates nothing, and
 * "leave the old password but take this new token" is not a state anyone needs.
 *
 * <p>A provider may authenticate by email/password, by a pre-issued token, or both — so all fields
 * are individually optional and the "at least a password or a token" rule is enforced in the
 * domain, where a non-REST caller cannot skip it.
 */
@Schema(name = "ProviderCredentialsRequest", description = "Replace a provider's partner credentials")
public record ProviderCredentialsRequest(

        @Schema(description = "Partner account email", example = "partner@roadscanner.com")
        @Size(max = 320, message = "partnerEmail must be at most 320 characters")
        String partnerEmail,

        @Schema(description = "Partner account password. Write-only — never returned by any endpoint.")
        String partnerPassword,

        @Schema(description = "Pre-issued partner token. Write-only — never returned by any endpoint.")
        String partnerToken
) {

    /**
     * Overridden because a record's generated {@code toString()} prints every component — this one
     * would render the password and token in the clear. That is one careless {@code log.debug(...)},
     * one framework that echoes a handler argument, or one exception message embedding the bound
     * object away from writing a partner secret to disk.
     *
     * <p>Presence is reported instead of value, which is all anyone debugging a failed credential
     * write actually needs.
     */
    @Override
    public String toString() {
        return "ProviderCredentialsRequest[email=" + (partnerEmail == null ? "absent" : "set")
                + ", password=" + (partnerPassword == null ? "absent" : "set")
                + ", token=" + (partnerToken == null ? "absent" : "set") + "]";
    }
}
