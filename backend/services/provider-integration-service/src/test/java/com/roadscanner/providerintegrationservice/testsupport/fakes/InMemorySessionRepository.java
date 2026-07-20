package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.SessionStatus;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySessionRepository implements SessionRepository {

    private final Map<ProviderSessionId, ProviderSession> sessions = new LinkedHashMap<>();

    @Override
    public Optional<ProviderSession> findById(ProviderSessionId id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public ProviderSession save(ProviderSession session) {
        sessions.put(session.id(), session);
        return session;
    }

    @Override
    public List<ProviderSession> findActiveExpiredAsOf(Instant asOf) {
        return sessions.values().stream()
                .filter(s -> s.status() == SessionStatus.ACTIVE && !s.token().expiresAt().isAfter(asOf))
                .toList();
    }
}
