package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Durable persistence port for {@link ProviderSession} — the source of truth {@link TokenCache}
 * sits in front of as a short-TTL read-through cache. */
public interface SessionRepository {

    Optional<ProviderSession> findById(ProviderSessionId id);

    ProviderSession save(ProviderSession session);

    /** Still-{@code ACTIVE} sessions whose token expired at or before {@code asOf} — backs
     * {@code SessionExpirySweeper}. */
    List<ProviderSession> findActiveExpiredAsOf(Instant asOf);
}
