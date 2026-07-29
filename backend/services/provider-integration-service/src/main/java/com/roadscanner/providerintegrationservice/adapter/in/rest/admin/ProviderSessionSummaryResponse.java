package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The outcome of an admin-triggered session refresh.
 *
 * <p>Carries the session's id and expiry, never its access token. An admin needs to know that
 * authentication succeeded and how long it holds — handing back the token itself would put a live
 * provider credential in a browser and in every proxy log along the way.
 */
@Schema(name = "ProviderSessionSummary", description = "A newly established provider session — never its token")
public record ProviderSessionSummaryResponse(

        @Schema(description = "Identifier of the new session")
        String sessionId,

        @Schema(description = "When this session's token stops being valid")
        Instant expiresAt) {
}
