package com.roadscanner.providerintegrationservice.adapter.in.rest.session;

import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;

import java.time.Instant;
import java.util.UUID;

public record AuthenticateProviderResponse(UUID sessionId, String providerType, Instant expiresAt) {

    public static AuthenticateProviderResponse from(AuthenticateProvider.Result result) {
        return new AuthenticateProviderResponse(result.sessionId().value(), result.providerType().code(), result.expiresAt());
    }
}
