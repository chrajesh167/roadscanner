package com.roadscanner.providerintegrationservice.application.usecase.session;

import com.roadscanner.providerintegrationservice.application.usecase.audit.AuditRecorder;
import com.roadscanner.providerintegrationservice.domain.model.AuditEventType;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SessionStatus;
import com.roadscanner.providerintegrationservice.testsupport.MutableClock;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryAuditRecordRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemorySessionRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryTokenCache;
import com.roadscanner.providerintegrationservice.testsupport.fakes.RecordingAuditPublisher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SessionExpirySweeperTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void expiresOnlyActiveSessionsPastTheirTokenExpiryAndPublishesSessionExpired() {
        MutableClock clock = new MutableClock(NOW);
        InMemorySessionRepository sessionRepository = new InMemorySessionRepository();
        InMemoryTokenCache tokenCache = new InMemoryTokenCache();
        RecordingAuditPublisher auditPublisher = new RecordingAuditPublisher();
        AuditRecorder auditRecorder = new AuditRecorder(new InMemoryAuditRecordRepository(), auditPublisher, clock);

        ProviderSessionId expiredId = ProviderSessionId.generate();
        ProviderSession expiredSession = ProviderSession.open(expiredId, ProviderType.MOCK,
                new ProviderToken("access", null, "Bearer", NOW.minusSeconds(1)), NOW.minusSeconds(100));
        sessionRepository.save(expiredSession);
        tokenCache.put(expiredId, expiredSession.token(), Duration.ofSeconds(1));

        ProviderSessionId stillValidId = ProviderSessionId.generate();
        sessionRepository.save(ProviderSession.open(stillValidId, ProviderType.MOCK,
                new ProviderToken("access", null, "Bearer", NOW.plusSeconds(3600)), NOW));

        SessionExpirySweeper sweeper = new SessionExpirySweeper(sessionRepository, tokenCache, auditRecorder, clock);
        int swept = sweeper.sweepExpiredSessions();

        assertThat(swept).isEqualTo(1);
        assertThat(sessionRepository.findById(expiredId)).get().extracting(ProviderSession::status)
                .isEqualTo(SessionStatus.EXPIRED);
        assertThat(sessionRepository.findById(stillValidId)).get().extracting(ProviderSession::status)
                .isEqualTo(SessionStatus.ACTIVE);
        assertThat(tokenCache.contains(expiredId)).isFalse();
        assertThat(auditPublisher.published()).hasSize(1);
        assertThat(auditPublisher.published().get(0).eventType()).isEqualTo(AuditEventType.SESSION_EXPIRED);
    }
}
