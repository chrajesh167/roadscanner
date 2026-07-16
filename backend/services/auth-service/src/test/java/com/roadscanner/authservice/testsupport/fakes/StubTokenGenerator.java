package com.roadscanner.authservice.testsupport.fakes;

import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic TokenGenerator double: raw values are {@code raw-token-N}, and the "hash" is a
 * visible prefixing — the same trick as {@link com.roadscanner.authservice.domain.port.out.StubPasswordHasher},
 * keeping use-case tests free of real crypto while preserving the generate/hashOf relationship
 * the flows rely on.
 */
public final class StubTokenGenerator implements TokenGenerator {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public GeneratedToken generate() {
        String raw = "raw-token-" + counter.incrementAndGet();
        return new GeneratedToken(raw, hashOf(raw));
    }

    @Override
    public TokenHash hashOf(String rawToken) {
        return new TokenHash("hash:" + rawToken);
    }
}
