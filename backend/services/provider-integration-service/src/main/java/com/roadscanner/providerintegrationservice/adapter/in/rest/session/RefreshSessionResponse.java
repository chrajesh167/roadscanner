package com.roadscanner.providerintegrationservice.adapter.in.rest.session;

import com.roadscanner.providerintegrationservice.domain.port.in.RefreshSession;

import java.time.Instant;
import java.util.UUID;

public record RefreshSessionResponse(UUID sessionId, Instant expiresAt) {

    public static RefreshSessionResponse from(RefreshSession.Result result) {
        return new RefreshSessionResponse(result.sessionId().value(), result.expiresAt());
    }
}
