package com.roadscanner.providerintegrationservice.application.usecase.session;

import com.roadscanner.providerintegrationservice.domain.exception.SessionExpiredException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.MutableClock;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemorySessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveSessionResolverTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemorySessionRepository sessionRepository = new InMemorySessionRepository();
    private final ActiveSessionResolver resolver = new ActiveSessionResolver(sessionRepository, clock);

    @Test
    void resolvesAnActiveNonExpiredSession() {
        ProviderSessionId id = ProviderSessionId.generate();
        ProviderToken token = new ProviderToken("access", null, "Bearer", NOW.plusSeconds(60));
        sessionRepository.save(ProviderSession.open(id, ProviderType.MOCK, token, NOW));

        assertThat(resolver.resolveActive(id).id()).isEqualTo(id);
    }

    @Test
    void rejectsAnUnknownSessionId() {
        assertThatThrownBy(() -> resolver.resolveActive(ProviderSessionId.generate()))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void rejectsARevokedSession() {
        ProviderSessionId id = ProviderSessionId.generate();
        ProviderToken token = new ProviderToken("access", null, "Bearer", NOW.plusSeconds(60));
        ProviderSession session = ProviderSession.open(id, ProviderType.MOCK, token, NOW);
        session.revoke(NOW);
        sessionRepository.save(session);

        assertThatThrownBy(() -> resolver.resolveActive(id)).isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void rejectsASessionWhoseTokenHasExpiredEvenIfStillMarkedActive() {
        ProviderSessionId id = ProviderSessionId.generate();
        ProviderToken token = new ProviderToken("access", null, "Bearer", NOW.plusSeconds(10));
        sessionRepository.save(ProviderSession.open(id, ProviderType.MOCK, token, NOW));
        clock.advanceBy(java.time.Duration.ofSeconds(20));

        assertThatThrownBy(() -> resolver.resolveActive(id)).isInstanceOf(SessionExpiredException.class);
    }
}
