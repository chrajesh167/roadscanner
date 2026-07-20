package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ProviderSessionRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProviderSessionRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    @Autowired
    private ProviderSessionRepositoryAdapter adapter;

    @Test
    void roundTripsANewlyOpenedSession() {
        ProviderSessionId id = ProviderSessionId.generate();
        ProviderToken token = new ProviderToken("access", "refresh", "Bearer", NOW.plusSeconds(3600));
        adapter.save(ProviderSession.open(id, ProviderType.MOCK, token, NOW));

        Optional<ProviderSession> found = adapter.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().providerType()).isEqualTo(ProviderType.MOCK);
        assertThat(found.get().token()).isEqualTo(token);
    }

    @Test
    void savingAnUpdateMutatesTheExistingRowRatherThanInsertingASecondOne() {
        ProviderSessionId id = ProviderSessionId.generate();
        ProviderToken originalToken = new ProviderToken("access-1", "refresh-1", "Bearer", NOW.plusSeconds(3600));
        ProviderSession session = ProviderSession.open(id, ProviderType.MOCK, originalToken, NOW);
        adapter.save(session);

        ProviderToken refreshedToken = new ProviderToken("access-2", "refresh-2", "Bearer", NOW.plusSeconds(7200));
        session.applyRefreshedToken(refreshedToken, NOW.plusSeconds(10));
        adapter.save(session);

        Optional<ProviderSession> found = adapter.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().token()).isEqualTo(refreshedToken);
    }

    @Test
    void findActiveExpiredAsOfReturnsOnlyActiveSessionsPastTheirExpiry() {
        ProviderSessionId expiredId = ProviderSessionId.generate();
        adapter.save(ProviderSession.open(expiredId, ProviderType.MOCK,
                new ProviderToken("a", null, "Bearer", NOW.minusSeconds(1)), NOW.minusSeconds(100)));

        ProviderSessionId activeId = ProviderSessionId.generate();
        adapter.save(ProviderSession.open(activeId, ProviderType.MOCK,
                new ProviderToken("a", null, "Bearer", NOW.plusSeconds(3600)), NOW));

        List<ProviderSession> expired = adapter.findActiveExpiredAsOf(NOW);

        assertThat(expired).extracting(ProviderSession::id).containsExactly(expiredId);
    }
}
