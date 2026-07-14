package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.PasswordHash;
import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.RefreshTokenId;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Exercises {@link RefreshTokenRepositoryAdapter} against a real Postgres (Testcontainers).
 * {@code refresh_tokens.user_id} has a foreign key to {@code credentials.id}, so every test
 * first persists a {@link Credential} via {@link CredentialRepositoryAdapter} to satisfy it.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, CredentialRepositoryAdapter.class, RefreshTokenRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class RefreshTokenRepositoryAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-14T10:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofDays(30));

    @Autowired
    private CredentialRepositoryAdapter credentialAdapter;

    @Autowired
    private RefreshTokenRepositoryAdapter adapter;

    @Autowired
    private RefreshTokenSpringDataRepository springDataRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private UserId persistedUserId() {
        Credential credential = Credential.register(UserId.generate(),
                new LoginIdentifier("owner-" + UUID.randomUUID() + "@example.com"),
                new PasswordHash("hash", "algo"), ISSUED_AT);
        credentialAdapter.save(credential);
        return credential.userId();
    }

    private RefreshToken newToken(UserId userId) {
        return RefreshToken.issue(RefreshTokenId.generate(), new TokenHash(UUID.randomUUID().toString()),
                userId, ISSUED_AT, EXPIRES_AT, DeviceMetadata.unknown());
    }

    @Test
    void saveInsertsANewRefreshToken() {
        RefreshToken token = newToken(persistedUserId());

        adapter.save(token);

        assertThat(adapter.findByTokenHash(token.tokenHash())).isPresent();
    }

    @Test
    void findByTokenHashReturnsEmptyWhenNotFound() {
        assertThat(adapter.findByTokenHash(new TokenHash(UUID.randomUUID().toString()))).isEmpty();
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersTokens() {
        UserId userA = persistedUserId();
        UserId userB = persistedUserId();
        RefreshToken tokenA1 = newToken(userA);
        RefreshToken tokenA2 = newToken(userA);
        RefreshToken tokenB = newToken(userB);
        adapter.save(tokenA1);
        adapter.save(tokenA2);
        adapter.save(tokenB);

        assertThat(adapter.findByUserId(userA))
                .extracting(RefreshToken::id)
                .containsExactlyInAnyOrder(tokenA1.id(), tokenA2.id());
    }

    @Test
    void saveUpdatesRevocationInPlace() {
        RefreshToken token = newToken(persistedUserId());
        adapter.save(token);

        token.revoke(ISSUED_AT.plusSeconds(60));
        adapter.save(token);

        RefreshToken reloaded = adapter.findByTokenHash(token.tokenHash()).orElseThrow();
        assertThat(reloaded.isRevoked()).isTrue();
    }

    @Test
    void rotationPersistsBothTheOldRevokedTokenAndTheNewOne() {
        UserId userId = persistedUserId();
        RefreshToken original = newToken(userId);
        adapter.save(original);

        RefreshToken rotated = original.rotate(RefreshTokenId.generate(),
                new TokenHash(UUID.randomUUID().toString()), ISSUED_AT.plusSeconds(60), EXPIRES_AT.plusSeconds(60));
        adapter.save(original); // now revoked
        adapter.save(rotated);

        assertThat(adapter.findByUserId(userId)).hasSize(2);
        RefreshToken reloadedOriginal = adapter.findByTokenHash(original.tokenHash()).orElseThrow();
        RefreshToken reloadedRotated = adapter.findByTokenHash(rotated.tokenHash()).orElseThrow();
        assertThat(reloadedOriginal.isRevoked()).isTrue();
        assertThat(reloadedRotated.isRevoked()).isFalse();
        assertThat(reloadedRotated.replaces()).contains(original.id());
    }

    @Test
    void duplicateTokenHashViolatesTheUniqueConstraint() {
        UserId userId = persistedUserId();
        TokenHash sharedHash = new TokenHash(UUID.randomUUID().toString());
        adapter.save(RefreshToken.issue(RefreshTokenId.generate(), sharedHash, userId, ISSUED_AT, EXPIRES_AT, DeviceMetadata.unknown()));

        RefreshToken duplicate = RefreshToken.issue(RefreshTokenId.generate(), sharedHash, userId, ISSUED_AT, EXPIRES_AT, DeviceMetadata.unknown());
        assertThatThrownBy(() -> {
            adapter.save(duplicate);
            testEntityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void foreignKeyRejectsATokenForANonExistentUser() {
        RefreshToken orphan = newToken(UserId.generate()); // never persisted as a Credential

        assertThatThrownBy(() -> {
            adapter.save(orphan);
            testEntityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    /**
     * The domain layer already rejects expires_at <= issued_at at construction time (see
     * RefreshTokenTest), so this invalid state can never reach the database through the normal
     * adapter/mapper path. This test bypasses the domain entirely, constructing the JPA entity
     * directly (this test class shares the entity's package, so its package-private constructor
     * is reachable) to prove the migration's CHECK constraint is real defense-in-depth, not
     * just a comment.
     */
    @Test
    void checkConstraintRejectsExpiryNotAfterIssuedAt() {
        UserId userId = persistedUserId();
        RefreshTokenJpaEntity invalid = new RefreshTokenJpaEntity(
                UUID.randomUUID(), UUID.randomUUID().toString(), userId.value(),
                ISSUED_AT, ISSUED_AT, null, null, null); // expiresAt == issuedAt, invalid

        // Unlike the TestEntityManager.flush() calls above, this goes through the Spring Data
        // repository proxy directly, which applies Spring's own exception translation — a
        // DataIntegrityViolationException here, not the raw Hibernate exception.
        assertThatThrownBy(() -> springDataRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
