package com.roadscanner.authservice.testsupport.fakes;

import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.RefreshTokenId;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Map-backed RefreshTokenRepository for framework-free use-case tests. */
public final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<RefreshTokenId, RefreshToken> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<RefreshToken> findByTokenHash(TokenHash tokenHash) {
        return byId.values().stream()
                .filter(token -> token.tokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public List<RefreshToken> findByUserId(UserId userId) {
        return byId.values().stream()
                .filter(token -> token.userId().equals(userId))
                .toList();
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        byId.put(refreshToken.id(), refreshToken);
        return refreshToken;
    }

    public List<RefreshToken> all() {
        return List.copyOf(byId.values());
    }
}
