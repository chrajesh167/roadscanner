package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.adapter.out.security.AesGcmCredentialCipher;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.CredentialEncryptionTestConfig;
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
        ProviderCredentialsRepositoryAdapter.class, CredentialEncryptionTestConfig.class,
        EncryptedCredentialConverter.class})
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
        // Written through the encrypting converter — the flag now records an accomplished fact.
        assertThat(found.isEncrypted()).isTrue();
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

    // ---------- Encryption at rest ----------

    @Test
    void storesSecretsEncryptedInTheDatabase() {
        Provider provider = register(uniqueType());
        credentials.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), provider.id(),
                "partner@roadscanner.com", "s3cret-password", "s3cret-token", NOW));
        reload();

        // Read the raw columns, bypassing JPA entirely — this is the only way to prove what is
        // actually on disk rather than what the converter hands back.
        Object[] raw = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("""
                        SELECT partner_password, partner_token, partner_email
                        FROM provider_credentials WHERE provider_id = :providerId
                        """)
                .setParameter("providerId", provider.id().value())
                .getSingleResult();

        String storedPassword = (String) raw[0];
        String storedToken = (String) raw[1];
        String storedEmail = (String) raw[2];

        assertThat(storedPassword).isNotNull().doesNotContain("s3cret-password")
                .startsWith(AesGcmCredentialCipher.SCHEME);
        assertThat(storedToken).isNotNull().doesNotContain("s3cret-token")
                .startsWith(AesGcmCredentialCipher.SCHEME);
        // Email is deliberately not encrypted — an account identifier, not a secret.
        assertThat(storedEmail).isEqualTo("partner@roadscanner.com");
    }

    @Test
    void returnsDecryptedSecretsWhenRead() {
        Provider provider = register(uniqueType());
        credentials.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), provider.id(),
                null, "s3cret-password", "s3cret-token", NOW));
        reload();

        ProviderCredentials found = credentials.findByProvider(provider.id()).orElseThrow();

        // Transparent: nothing above the persistence layer decrypts anything.
        assertThat(found.partnerPassword()).contains("s3cret-password");
        assertThat(found.partnerToken()).contains("s3cret-token");
        assertThat(found.isEncrypted()).isTrue();
    }

    @Test
    void encryptsSecretsOnRotationToo() {
        Provider provider = register(uniqueType());
        ProviderCredentials stored = credentials.save(ProviderCredentials.issue(
                ProviderCredentialsId.generate(), provider.id(), null, "original", null, NOW));

        stored.rotate(null, null, "rotated-token", NOW.plusSeconds(60));
        credentials.save(stored);
        reload();

        String storedToken = (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT partner_token FROM provider_credentials WHERE provider_id = :providerId")
                .setParameter("providerId", provider.id().value())
                .getSingleResult();

        // Every write path encrypts, not just the first — the converter is on the column, so a
        // new write path cannot forget.
        assertThat(storedToken).doesNotContain("rotated-token").startsWith(AesGcmCredentialCipher.SCHEME);
        assertThat(credentials.findByProvider(provider.id()).orElseThrow().partnerToken())
                .contains("rotated-token");
    }

    @Test
    void readsLegacyPlaintextRowsWrittenBeforeEncryption() {
        Provider provider = register(uniqueType());
        reload();

        // Simulates a row written before Sprint 2.1 — inserted straight past the converter.
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO provider_credentials
                  (id, provider_id, partner_email, partner_password, partner_token, encrypted, created_at, updated_at)
                VALUES (gen_random_uuid(), :providerId, NULL, 'legacy-plaintext', NULL, false, now(), now())
                """)
                .setParameter("providerId", provider.id().value())
                .executeUpdate();
        reload();

        // Enabling encryption must not have bricked existing credentials.
        assertThat(credentials.findByProvider(provider.id()).orElseThrow().partnerPassword())
                .contains("legacy-plaintext");
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
