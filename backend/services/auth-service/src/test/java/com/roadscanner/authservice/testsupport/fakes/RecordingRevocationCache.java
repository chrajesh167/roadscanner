package com.roadscanner.authservice.testsupport.fakes;

import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.port.out.RevocationCache;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory RevocationCache that records what was marked, for assertions. */
public final class RecordingRevocationCache implements RevocationCache {

    private final Map<TokenHash, Instant> revoked = new ConcurrentHashMap<>();

    @Override
    public void markRevoked(TokenHash tokenHash, Instant expiresAt) {
        revoked.put(tokenHash, expiresAt);
    }

    @Override
    public boolean isRevoked(TokenHash tokenHash) {
        return revoked.containsKey(tokenHash);
    }

    public Set<TokenHash> revokedHashes() {
        return Set.copyOf(revoked.keySet());
    }
}
