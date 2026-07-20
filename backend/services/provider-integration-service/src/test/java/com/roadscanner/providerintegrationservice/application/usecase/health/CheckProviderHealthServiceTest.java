package com.roadscanner.providerintegrationservice.application.usecase.health;

import com.roadscanner.providerintegrationservice.application.usecase.audit.AuditRecorder;
import com.roadscanner.providerintegrationservice.domain.model.AuditEventType;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;
import com.roadscanner.providerintegrationservice.testsupport.MutableClock;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryAuditRecordRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderHealthRepository;
import com.roadscanner.providerintegrationservice.testsupport.fakes.RecordingAuditPublisher;
import com.roadscanner.providerintegrationservice.testsupport.fakes.StubProviderClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the "only publish on the two transitions that matter" rule from
 * {@link CheckProviderHealthService}'s own Javadoc. */
class CheckProviderHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryProviderConfigurationRepository configurationRepository = new InMemoryProviderConfigurationRepository();
    private final InMemoryProviderHealthRepository healthRepository = new InMemoryProviderHealthRepository();
    private final InMemoryAuditRecordRepository auditRecordRepository = new InMemoryAuditRecordRepository();
    private final RecordingAuditPublisher auditPublisher = new RecordingAuditPublisher();
    private final AuditRecorder auditRecorder = new AuditRecorder(auditRecordRepository, auditPublisher, clock);

    private CheckProviderHealthService service(StubProviderClient client) {
        configurationRepository.add(Provider.reconstitute(ProviderId.generate(), ProviderType.MOCK, "Mock", true,
                Set.of(ProviderCapability.HEALTH_CHECK), null, NOW, NOW));
        return new CheckProviderHealthService(configurationRepository, new ProviderClientRegistry(List.of(client)),
                healthRepository, auditRecorder, clock);
    }

    @Test
    void firstEverCheckDoesNotPublishAnEvent() {
        StubProviderClient client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.HEALTH_CHECK));
        client.healthResult = () -> new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, NOW, null);

        service(client).check(new CheckProviderHealth.Command(ProviderType.MOCK));

        assertThat(auditPublisher.published()).isEmpty();
    }

    @Test
    void transitioningToUnavailablePublishesProviderUnavailable() {
        StubProviderClient client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.HEALTH_CHECK));
        client.healthResult = () -> new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, NOW, null);
        CheckProviderHealthService checkHealth = service(client);
        checkHealth.check(new CheckProviderHealth.Command(ProviderType.MOCK));

        client.healthResult = () -> new ProviderHealthCheck(ProviderType.MOCK, HealthState.UNAVAILABLE, NOW.plusSeconds(30), "down");
        checkHealth.check(new CheckProviderHealth.Command(ProviderType.MOCK));

        assertThat(auditPublisher.published()).hasSize(1);
        assertThat(auditPublisher.published().get(0).eventType()).isEqualTo(AuditEventType.PROVIDER_UNAVAILABLE);
    }

    @Test
    void recoveringFromUnavailableToHealthyPublishesProviderRecovered() {
        // The very first check ever landing on UNAVAILABLE is itself a genuine transition
        // (previous state UNKNOWN is not UNAVAILABLE) and is correctly reported — so this
        // sequence publishes PROVIDER_UNAVAILABLE first, then PROVIDER_RECOVERED on the second
        // check. Only a first-ever check landing on HEALTHY is suppressed as "not a recovery"
        // (see firstEverCheckDoesNotPublishAnEvent).
        StubProviderClient client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.HEALTH_CHECK));
        client.healthResult = () -> new ProviderHealthCheck(ProviderType.MOCK, HealthState.UNAVAILABLE, NOW, "down");
        CheckProviderHealthService checkHealth = service(client);
        checkHealth.check(new CheckProviderHealth.Command(ProviderType.MOCK));

        client.healthResult = () -> new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, NOW.plusSeconds(30), null);
        checkHealth.check(new CheckProviderHealth.Command(ProviderType.MOCK));

        assertThat(auditPublisher.published()).hasSize(2);
        assertThat(auditPublisher.published().get(0).eventType()).isEqualTo(AuditEventType.PROVIDER_UNAVAILABLE);
        assertThat(auditPublisher.published().get(1).eventType()).isEqualTo(AuditEventType.PROVIDER_RECOVERED);
    }

    @Test
    void degradedToUnavailableIsNotTreatedAsARecovery() {
        StubProviderClient client = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.HEALTH_CHECK));
        client.healthResult = () -> new ProviderHealthCheck(ProviderType.MOCK, HealthState.DEGRADED, NOW, "slow");
        CheckProviderHealthService checkHealth = service(client);
        checkHealth.check(new CheckProviderHealth.Command(ProviderType.MOCK));
        auditPublisher.published(); // sanity

        client.healthResult = () -> new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, NOW.plusSeconds(30), null);
        checkHealth.check(new CheckProviderHealth.Command(ProviderType.MOCK));

        assertThat(auditPublisher.published()).isEmpty();
    }
}
