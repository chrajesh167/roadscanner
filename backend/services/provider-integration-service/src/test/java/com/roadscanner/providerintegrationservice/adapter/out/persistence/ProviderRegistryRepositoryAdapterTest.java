package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * The registry against a real Postgres — V6's new columns, the credentials table, and the
 * constraints that only a database can enforce.
 *
 * <p>Also validates V6 against the JPA entities: {@code ddl-auto: validate} means a column mapped
 * differently from the migration fails here rather than at first deploy.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, ProviderConfigurationRepositoryAdapter.class,
        ProviderCredentialsRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProviderRegistryRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    @Autowired
    private ProviderConfigurationRepositoryAdapter providers;

    @Autowired
    private ProviderCredentialsRepositoryAdapter credentials;

    @Autowired
    private TestEntityManager entityManager;

    /** Forces pending writes out and drops the first-level cache, so the next read is real. */
    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private static ProviderType uniqueType() {
        return new ProviderType("TEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
    }

    private Provider register(ProviderType type) {
        return providers.save(Provider.register(ProviderId.generate(), type, ProviderCategory.BUS, "Test Provider",
                Set.of(ProviderCapability.SEARCH, ProviderCapability.SEAT_MAP), "https://partner.test",
                7_500, 3, NOW));
    }

    @Test
    void savesAndRoundTripsEveryRegistryField() {
        ProviderType type = uniqueType();
        Provider saved = register(type);
        reload();

        Provider found = providers.findById(saved.id()).orElseThrow();
        assertThat(found.type()).isEqualTo(type);
        assertThat(found.category()).isEqualTo(ProviderCategory.BUS);
        assertThat(found.displayName()).isEqualTo("Test Provider");
        assertThat(found.enabled()).isFalse();
        assertThat(found.capabilities())
                .containsExactlyInAnyOrder(ProviderCapability.SEARCH, ProviderCapability.SEAT_MAP);
        assertThat(found.baseUrl()).isEqualTo("https://partner.test");
        assertThat(found.timeoutMillis()).isEqualTo(7_500);
        assertThat(found.retryCount()).isEqualTo(3);
        assertThat(found.createdAt()).isEqualTo(NOW);
    }

    @Test
    void findsTheSeededFlixbusRowWithV6Defaults() {
        // V5 seeded FLIXBUS; V6 backfilled its category and resilience settings. Enabling it was
        // deliberately left to the admin API — its base URL is still a placeholder.
        Provider flixbus = providers.findByType(ProviderType.FLIXBUS).orElseThrow();

        assertThat(flixbus.category()).isEqualTo(ProviderCategory.BUS);
        assertThat(flixbus.timeoutMillis()).isEqualTo(8_000);
        assertThat(flixbus.retryCount()).isEqualTo(2);
        assertThat(flixbus.enabled()).isFalse();
    }

    @Test
    void findByIdIsEmptyForAnUnknownId() {
        assertThat(providers.findById(ProviderId.generate())).isEmpty();
    }

    @Test
    void saveUpdatesTheExistingRowRatherThanInsertingASecondOne() {
        Provider saved = register(uniqueType());
        long before = providers.findAll().size();

        saved.update(ProviderCategory.RAIL, "Renamed", Set.of(ProviderCapability.SEARCH),
                "https://rail.test", 2_000, 1, NOW.plusSeconds(60));
        saved.enable(NOW.plusSeconds(60));
        providers.save(saved);
        reload();

        assertThat(providers.findAll()).hasSize((int) before);
        Provider found = providers.findById(saved.id()).orElseThrow();
        assertThat(found.displayName()).isEqualTo("Renamed");
        assertThat(found.category()).isEqualTo(ProviderCategory.RAIL);
        assertThat(found.enabled()).isTrue();
        assertThat(found.timeoutMillis()).isEqualTo(2_000);
        assertThat(found.createdAt()).isEqualTo(NOW);
    }

    @Test
    void existsByTypeBacksTheDuplicatePreCheck() {
        ProviderType type = uniqueType();
        assertThat(providers.existsByType(type)).isFalse();

        register(type);
        reload();

        assertThat(providers.existsByType(type)).isTrue();
    }

    @Test
    void theUniqueConstraintRejectsASecondProviderWithTheSameCode() {
        ProviderType type = uniqueType();
        register(type);

        // The application pre-checks for a friendly 409, but the constraint is the real guarantee
        // under concurrency.
        assertThatThrownBy(() -> {
            providers.save(Provider.register(ProviderId.generate(), type, ProviderCategory.BUS, "Duplicate",
                    Set.of(), null, 5_000, 2, NOW));
            entityManager.flush();
        }).hasStackTraceContaining("uq_provider_configurations_type");
    }

    @Test
    void theCheckConstraintRejectsANonPositiveTimeout() {
        // The domain rejects this too; this proves the database would still catch a row written
        // by anything that bypassed the domain.
        assertThatThrownBy(() -> {
            entityManager.getEntityManager().createNativeQuery("""
                    INSERT INTO provider_configurations
                      (id, provider_type, provider_category, display_name, enabled, capabilities,
                       timeout_ms, retry_count, created_at, updated_at, version)
                    VALUES (gen_random_uuid(), 'BADTIMEOUT', 'BUS', 'Bad', false, 'SEARCH', 0, 2, now(), now(), 0)
                    """).executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("chk_provider_configurations_timeout_positive");
    }

    @Test
    void theCheckConstraintBoundsRetryCount() {
        assertThatThrownBy(() -> {
            entityManager.getEntityManager().createNativeQuery("""
                    INSERT INTO provider_configurations
                      (id, provider_type, provider_category, display_name, enabled, capabilities,
                       timeout_ms, retry_count, created_at, updated_at, version)
                    VALUES (gen_random_uuid(), 'BADRETRY', 'BUS', 'Bad', false, 'SEARCH', 5000, 99, now(), now(), 0)
                    """).executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("chk_provider_configurations_retry_bounded");
    }

    @Test
    void roundTripsCredentials() {
        Provider provider = register(uniqueType());
        credentials.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), provider.id(),
                "partner@roadscanner.com", "s3cret", "token-abc", NOW));
        reload();

        ProviderCredentials found = credentials.findByProvider(provider.id()).orElseThrow();
        assertThat(found.partnerEmail()).contains("partner@roadscanner.com");
        assertThat(found.hasPassword()).isTrue();
        assertThat(found.hasToken()).isTrue();
        assertThat(found.isEncrypted()).isFalse();
    }

    @Test
    void credentialsAreEmptyForAProviderThatHasNone() {
        Provider provider = register(uniqueType());

        assertThat(credentials.findByProvider(provider.id())).isEmpty();
    }

    @Test
    void savingCredentialsTwiceRotatesRatherThanDuplicating() {
        Provider provider = register(uniqueType());
        ProviderCredentials stored = credentials.save(ProviderCredentials.issue(
                ProviderCredentialsId.generate(), provider.id(), "old@roadscanner.com", "old", null, NOW));

        stored.rotate("new@roadscanner.com", null, "token-new", NOW.plusSeconds(60));
        credentials.save(stored);
        reload();

        ProviderCredentials found = credentials.findByProvider(provider.id()).orElseThrow();
        assertThat(found.partnerEmail()).contains("new@roadscanner.com");
        assertThat(found.hasPassword()).isFalse();
        assertThat(found.partnerToken()).contains("token-new");
        assertThat(found.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void theUniqueConstraintAllowsOnlyOneCredentialSetPerProvider() {
        Provider provider = register(uniqueType());
        credentials.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), provider.id(),
                null, "first", null, NOW));

        // Two rows would leave "which one authenticates" ambiguous.
        assertThatThrownBy(() -> {
            credentials.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), provider.id(),
                    null, "second", null, NOW));
            entityManager.flush();
        }).hasStackTraceContaining("uq_provider_credentials_provider");
    }

    @Test
    void theForeignKeyRejectsCredentialsForAnUnknownProvider() {
        assertThatThrownBy(() -> {
            credentials.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), ProviderId.generate(),
                    null, "orphan", null, NOW));
            entityManager.flush();
        }).hasStackTraceContaining("fk_provider_credentials_provider");
    }
}
