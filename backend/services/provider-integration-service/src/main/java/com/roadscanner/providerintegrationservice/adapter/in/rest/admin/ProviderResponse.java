package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A registry entry as the admin console sees it.
 *
 * <p>Carries no credential of any kind — not the password, not the token, not even whether a
 * particular secret's value looks right. Credential state is reported separately and only as
 * presence flags; see {@code ProviderCredentialsResponse}.
 */
@Schema(name = "Provider", description = "A configured provider in the RoadScanner registry")
public record ProviderResponse(

        @Schema(description = "Registry id — the identifier the admin API addresses a provider by")
        String id,

        @Schema(description = "The provider's unique code", example = "FLIXBUS")
        String code,

        @Schema(description = "Transport vertical", example = "BUS")
        String category,

        String displayName,

        @Schema(description = "False when the provider is registered but not in service")
        boolean enabled,

        @Schema(description = "Supported capabilities, alphabetical")
        List<String> capabilities,

        String baseUrl,
        int timeoutMs,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProviderResponse from(Provider provider) {
        return new ProviderResponse(
                provider.id().toString(),
                provider.type().code(),
                provider.category().code(),
                provider.displayName(),
                provider.enabled(),
                sortedNames(provider.capabilities()),
                provider.baseUrl(),
                provider.timeoutMillis(),
                provider.retryCount(),
                provider.createdAt(),
                provider.updatedAt());
    }

    /** Sorted so the payload is stable between calls — an unordered set would make responses
     * differ for no reason and defeat client-side diffing. */
    private static List<String> sortedNames(Set<ProviderCapability> capabilities) {
        return capabilities.stream().map(Enum::name).sorted().toList();
    }
}
