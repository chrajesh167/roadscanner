package com.roadscanner.providerintegrationservice.application.usecase.session;

import com.roadscanner.providerintegrationservice.application.usecase.audit.AuditRecorder;
import com.roadscanner.providerintegrationservice.domain.model.AuditEventType;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;

import java.time.Clock;
import java.util.List;

/**
 * Marks every still-{@code ACTIVE} session whose token has expired as {@link
 * com.roadscanner.providerintegrationservice.domain.model.SessionStatus#EXPIRED}, evicts it from
 * {@code TokenCache}, and publishes {@code SessionExpired} for each — invoked on a schedule by
 * {@code config.ProviderHealthMonitorScheduler}'s sibling bean (see {@code config} package).
 * Framework-free by design, matching this codebase's convention that scheduling is a config-layer
 * concern and the business logic it triggers is not.
 */
public class SessionExpirySweeper {

    private final SessionRepository sessionRepository;
    private final TokenCache tokenCache;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public SessionExpirySweeper(SessionRepository sessionRepository, TokenCache tokenCache, AuditRecorder auditRecorder,
                                 Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenCache = tokenCache;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    public int sweepExpiredSessions() {
        List<ProviderSession> expired = sessionRepository.findActiveExpiredAsOf(clock.instant());
        for (ProviderSession session : expired) {
            session.expire(clock.instant());
            sessionRepository.save(session);
            tokenCache.evict(session.id());
            auditRecorder.record(session.providerType(), AuditEventType.SESSION_EXPIRED, session.id(),
                    "Session " + session.id() + " expired (token past its expiry and never refreshed)");
        }
        return expired.size();
    }
}
