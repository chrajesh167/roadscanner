package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Write shape for registering and updating a provider.
 *
 * <p>{@code code} is accepted on create and ignored on update — a provider's code is its identity,
 * and every session and health row is keyed on it. See {@code Provider#update}.
 *
 * <p>Carries no credentials: those go to {@code PUT /providers/{id}/credentials}, so that a
 * routine display-name edit never has to resend secrets, and so credential writes are separately
 * auditable.
 */
@Schema(name = "ProviderRequest", description = "Register or update a provider")
public record ProviderRequest(

        @Schema(description = "Unique provider code. Required on create, ignored on update.",
                example = "FLIXBUS")
        @Size(max = 50, message = "code must be at most 50 characters")
        String code,

        @Schema(description = "Transport vertical", example = "BUS")
        @NotBlank(message = "category is required")
        @Size(max = 50, message = "category must be at most 50 characters")
        String category,

        @Schema(description = "Human-readable name", example = "FlixBus")
        @NotBlank(message = "displayName is required")
        @Size(max = 255, message = "displayName must be at most 255 characters")
        String displayName,

        @Schema(description = "Supported capabilities",
                example = "[\"SEARCH\",\"SEAT_MAP\",\"BOOKING_CONFIRMATION\"]")
        @NotEmpty(message = "at least one capability is required")
        Set<String> capabilities,

        @Schema(description = "Provider API base URL", example = "https://partner.flixbus.com")
        @Size(max = 500, message = "baseUrl must be at most 500 characters")
        String baseUrl,

        @Schema(description = "Per-request timeout in milliseconds", example = "8000")
        @Min(value = 1, message = "timeoutMs must be greater than zero")
        @Max(value = 120_000, message = "timeoutMs must be at most 120000")
        Integer timeoutMs,

        @Schema(description = "Retries before giving up (0-5)", example = "2")
        @Min(value = 0, message = "retryCount must be at least 0")
        @Max(value = 5, message = "retryCount must be at most 5")
        Integer retryCount
) {

    /** Defaults match V6's column defaults, so an omitted field means the same thing whether a row
     * arrives through this API or through a migration. */
    public int timeoutMsOrDefault() {
        return timeoutMs != null ? timeoutMs : 5_000;
    }

    public int retryCountOrDefault() {
        return retryCount != null ? retryCount : 2;
    }
}
