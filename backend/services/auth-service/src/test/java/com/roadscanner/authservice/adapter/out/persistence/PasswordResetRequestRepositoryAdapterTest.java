package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.PasswordHash;
import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.PasswordResetRequestId;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.testsupport.TestcontainersConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
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
 * Exercises {@link PasswordResetRequestRepositoryAdapter} against a real Postgres
 * (Testcontainers). {@code password_reset_requests.user_id} has a foreign key to
 * {@code credentials.id}, so every test first persists a {@link Credential}.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, CredentialRepositoryAdapter.class, PasswordResetRequestRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PasswordResetRequestRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofHours(1));

    @Autowired
    private CredentialRepositoryAdapter credentialAdapter;

    @Autowired
    private PasswordResetRequestRepositoryAdapter adapter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TestEntityManager testEntityManager;

    private UserId persistedUserId() {
        Credential credential = Credential.register(UserId.generate(),
                new LoginIdentifier("owner-" + UUID.randomUUID() + "@example.com"),
                new PasswordHash("hash", "algo"), NOW);
        credentialAdapter.save(credential);
        return credential.userId();
    }

    private PasswordResetRequest newRequest(UserId userId) {
        return PasswordResetRequest.issue(PasswordResetRequestId.generate(),
                new TokenHash(UUID.randomUUID().toString()), userId, EXPIRES_AT);
    }

    @Test
    void saveInsertsANewRequest() {
        PasswordResetRequest request = newRequest(persistedUserId());

        adapter.save(request);

        assertThat(adapter.findByTokenHash(request.tokenHash())).isPresent();
    }

    @Test
    void findByTokenHashReturnsEmptyWhenNotFound() {
        assertThat(adapter.findByTokenHash(new TokenHash(UUID.randomUUID().toString()))).isEmpty();
    }

    @Test
    void saveUpdatesUsedAtInPlace() {
        PasswordResetRequest request = newRequest(persistedUserId());
        adapter.save(request);

        request.use(NOW.plusSeconds(60));
        adapter.save(request);

        PasswordResetRequest reloaded = adapter.findByTokenHash(request.tokenHash()).orElseThrow();
        assertThat(reloaded.isUsed()).isTrue();
        assertThat(reloaded.usedAt()).contains(NOW.plusSeconds(60));
    }

    @Test
    void duplicateTokenHashViolatesTheUniqueConstraint() {
        UserId userId = persistedUserId();
        TokenHash sharedHash = new TokenHash(UUID.randomUUID().toString());
        adapter.save(PasswordResetRequest.issue(PasswordResetRequestId.generate(), sharedHash, userId, EXPIRES_AT));

        PasswordResetRequest duplicate = PasswordResetRequest.issue(PasswordResetRequestId.generate(), sharedHash, userId, EXPIRES_AT);
        // A direct TestEntityManager.flush() bypasses the @Repository proxy that would
        // normally translate this into a Spring DataAccessException — asserting on Hibernate's
        // own exception type here is what genuinely happens on this call path.
        assertThatThrownBy(() -> {
            adapter.save(duplicate);
            testEntityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    /**
     * The security-critical scenario documented in
     * {@link PasswordResetRequestRepositoryAdapter}'s Javadoc: two concurrent confirm-password-
     * reset attempts both read the request while it's still unused, so the domain's own
     * {@code use()} invariant succeeds for both in isolation — only the database's optimistic
     * lock, checked at each transaction's commit, actually prevents the second one from
     * silently succeeding too.
     *
     * Uses real threads for the same reason {@code CredentialRepositoryAdapterTest}'s
     * equivalent test does — see that test's Javadoc. The owning Credential is persisted via
     * its own committing REQUIRES_NEW transaction first: if it were saved on @DataJpaTest's
     * ambient (uncommitted-until-teardown) transaction instead, the separate REQUIRES_NEW
     * transactions below would not be able to see it and would fail the foreign key check
     * before the optimistic-locking behavior under test ever ran.
     */
    @Test
    void concurrentDoubleUseIsPreventedByOptimisticLocking() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        UserId userId = tx.execute(status -> {
            Credential credential = Credential.register(UserId.generate(),
                    new LoginIdentifier("owner-" + UUID.randomUUID() + "@example.com"),
                    new PasswordHash("hash", "algo"), NOW);
            credentialAdapter.save(credential);
            return credential.userId();
        });
        TokenHash tokenHash = new TokenHash(UUID.randomUUID().toString());
        tx.executeWithoutResult(status ->
                adapter.save(PasswordResetRequest.issue(PasswordResetRequestId.generate(), tokenHash, userId, EXPIRES_AT)));

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch firstWriteCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstWriter = executor.submit(() -> {
                tx.executeWithoutResult(status -> {
                    PasswordResetRequest r = adapter.findByTokenHash(tokenHash).orElseThrow();
                    bothRead.countDown();
                    awaitUninterruptibly(bothRead);
                    r.use(NOW.plusSeconds(60));
                    adapter.save(r);
                });
                firstWriteCommitted.countDown();
            });

            Future<?> secondWriter = executor.submit(() -> tx.executeWithoutResult(status -> {
                PasswordResetRequest r = adapter.findByTokenHash(tokenHash).orElseThrow();
                bothRead.countDown();
                awaitUninterruptibly(bothRead);
                awaitUninterruptibly(firstWriteCommitted);
                r.use(NOW.plusSeconds(90)); // succeeds in-memory — this thread's view predates the first commit
                adapter.save(r);
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
