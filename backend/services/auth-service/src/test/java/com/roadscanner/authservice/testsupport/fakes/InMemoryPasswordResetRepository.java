package com.roadscanner.authservice.testsupport.fakes;

import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.PasswordResetRequestId;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.port.out.PasswordResetRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Map-backed PasswordResetRepository for framework-free use-case tests. */
public final class InMemoryPasswordResetRepository implements PasswordResetRepository {

    private final Map<PasswordResetRequestId, PasswordResetRequest> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<PasswordResetRequest> findByTokenHash(TokenHash tokenHash) {
        return byId.values().stream()
                .filter(request -> request.tokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public PasswordResetRequest save(PasswordResetRequest request) {
        byId.put(request.id(), request);
        return request;
    }

    public List<PasswordResetRequest> all() {
        return List.copyOf(byId.values());
    }
}
