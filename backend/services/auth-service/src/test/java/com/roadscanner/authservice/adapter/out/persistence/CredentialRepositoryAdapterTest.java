package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.AccountStatus;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.PasswordHash;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.testsupport.TestcontainersConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Exercises {@link CredentialRepositoryAdapter} against a real Postgres (Testcontainers), not a
 * mock or an in-memory substitute — per docs/services/auth-service/testing-strategy.md,
 * integration tests must prove what production will actually do.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, CredentialRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CredentialRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    @Autowired
    private CredentialRepositoryAdapter adapter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TestEntityManager testEntityManager;

    private Credential newCredential(String loginIdentifier) {
        return Credential.register(UserId.generate(), new LoginIdentifier(loginIdentifier),
                new PasswordHash("hashed-value", "stub-algorithm"), NOW);
    }

    @Test
    void saveInsertsANewCredential() {
        Credential credential = newCredential("insert-" + UUID.randomUUID() + "@example.com");

        adapter.save(credential);

        assertThat(adapter.findByUserId(credential.userId())).isPresent();
    }

    @Test
    void roundTripPreservesAllFields() {
        Credential credential = newCredential("roundtrip-" + UUID.randomUUID() + "@example.com");
        adapter.save(credential);

        Credential reloaded = adapter.findByUserId(credential.userId()).orElseThrow();

        assertThat(reloaded.userId()).isEqualTo(credential.userId());
        assertThat(reloaded.loginIdentifier()).isEqualTo(credential.loginIdentifier());
        assertThat(reloaded.passwordHash()).isEqualTo(credential.passwordHash());
        assertThat(reloaded.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.failedLoginAttempts()).isZero();
        assertThat(reloaded.createdAt()).isEqualTo(credential.createdAt());
        assertThat(reloaded.updatedAt()).isEqualTo(credential.updatedAt());
        assertThat(reloaded.lastLoginAt()).isEmpty();
    }

    @Test
    void saveUpdatesAnExistingCredentialInPlace() {
        Credential credential = newCredential("update-" + UUID.randomUUID() + "@example.com");
        adapter.save(credential);

        credential.suspend(NOW.plusSeconds(60));
        adapter.save(credential);

        Credential reloaded = adapter.findByUserId(credential.userId()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void findByLoginIdentifierFindsAnExistingCredential() {
        String identifier = "find-" + UUID.randomUUID() + "@example.com";
        Credential credential = newCredential(identifier);
        adapter.save(credential);

        assertThat(adapter.findByLoginIdentifier(new LoginIdentifier(identifier)))
                .contains(credential);
    }

    @Test
    void findByLoginIdentifierReturnsEmptyWhenNotFound() {
        assertThat(adapter.findByLoginIdentifier(new LoginIdentifier("nobody-" + UUID.randomUUID() + "@example.com")))
                .isEmpty();
    }

    @Test
    void findByUserIdReturnsEmptyWhenNotFound() {
        assertThat(adapter.findByUserId(UserId.generate())).isEmpty();
    }

    @Test
    void existsByLoginIdentifierReflectsRegistrationState() {
        String identifier = "exists-" + UUID.randomUUID() + "@example.com";
        LoginIdentifier loginIdentifier = new LoginIdentifier(identifier);
        assertThat(adapter.existsByLoginIdentifier(loginIdentifier)).isFalse();

        adapter.save(newCredential(identifier));

        assertThat(adapter.existsByLoginIdentifier(loginIdentifier)).isTrue();
    }

    @Test
    void duplicateLoginIdentifierViolatesTheUniqueConstraint() {
        String identifier = "dup-" + UUID.randomUUID() + "@example.com";
        adapter.save(newCredential(identifier));

        // A direct TestEntityManager.flush() bypasses the @Repository proxy that would
        // normally translate this into a Spring DataAccessException — asserting on Hibernate's
        // own exception type here is what genuinely happens on this call path, not a guess.
        assertThatThrownBy(() -> {
            adapter.save(newCredential(identifier));
            testEntityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    /**
     * Proves the @Version column actually protects against a lost update between two
     * genuinely concurrent transactions — not just that the annotation is present.
     *
     * Uses real threads, not sequential calls: {@link CredentialRepositoryAdapter#save} always
     * re-reads the current row immediately before applying changes, so if "read" and "save"
     * were split across two *sequential*, independently-committing transactions, the second
     * save's own internal read would simply pick up the first save's already-committed change
     * and silently succeed — proving nothing. The version check only does real work when a
     * transaction's read and its later save share one persistence context (exactly how a
     * single request's transaction boundary normally works), which is what this test recreates
     * by keeping each thread's whole read-then-write sequence inside one transaction, and using
     * latches to guarantee both threads have read the *same* pre-update row before either
     * writes.
     */
    @Test
    void concurrentUpdatesAreDetectedByOptimisticLocking() throws Exception {
        Credential credential = newCredential("race-" + UUID.randomUUID() + "@example.com");
        UserId userId = credential.userId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> adapter.save(credential));

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch firstWriteCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstWriter = executor.submit(() -> {
                tx.executeWithoutResult(status -> {
                    Credential c = adapter.findByUserId(userId).orElseThrow();
                    bothRead.countDown();
                    awaitUninterruptibly(bothRead);  // wait for the other thread to also read
                    c.suspend(NOW.plusSeconds(60));
                    adapter.save(c);  // commits when this callback returns
                });
                firstWriteCommitted.countDown();
            });

            Future<?> secondWriter = executor.submit(() -> tx.executeWithoutResult(status -> {
                Credential c = adapter.findByUserId(userId).orElseThrow();
                bothRead.countDown();
                awaitUninterruptibly(bothRead);
                awaitUninterruptibly(firstWriteCommitted);  // wait until the first thread has definitely committed
                c.suspend(NOW.plusSeconds(90));
                adapter.save(c);   // should fail — its read is now stale
            }));

            firstWriter.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> secondWriter.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
