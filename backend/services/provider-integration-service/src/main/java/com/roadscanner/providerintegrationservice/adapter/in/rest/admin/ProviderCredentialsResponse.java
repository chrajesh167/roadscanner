package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * What the admin console is told about a provider's credentials: whether they exist, and when they
 * last changed.
 *
 * <p>Never the values. Credentials are write-only over HTTP by design — an admin can replace them
 * but cannot read them back, so a compromised admin session cannot be used to exfiltrate every
 * partner secret in the platform. This record deliberately has no field that could hold one, which
 * is a stronger guarantee than remembering not to populate it.
 */
@Schema(name = "ProviderCredentials", description = "Presence and freshness of a provider's credentials — never the values")
public record ProviderCredentialsResponse(

        @Schema(description = "True when a partner password is stored")
        boolean hasPassword,

        @Schema(description = "True when a partner token is stored")
        boolean hasToken,

        @Schema(description = "Whether these secrets are encrypted at rest. True for anything "
                + "written since Sprint 2.1; rows predating it report false until their next write.")
        boolean encrypted,

        Instant updatedAt
) {

    public static ProviderCredentialsResponse from(ManageProviderCredentials.CredentialsSummary summary) {
        return new ProviderCredentialsResponse(summary.hasPassword(), summary.hasToken(), summary.encrypted(),
                summary.updatedAt());
    }
}
