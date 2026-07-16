package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.PasswordHash;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.testsupport.TestcontainersConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Exercises {@link RoleAssignmentRepositoryAdapter} against a real Postgres (Testcontainers).
 * {@code role_assignments.user_id} has a foreign key to {@code credentials.id}, so every test
 * first persists a {@link Credential}.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, CredentialRepositoryAdapter.class, RoleAssignmentRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class RoleAssignmentRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    @Autowired
    private CredentialRepositoryAdapter credentialAdapter;

    @Autowired
    private RoleAssignmentRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager testEntityManager;

    private UserId persistedUserId() {
        Credential credential = Credential.register(UserId.generate(),
                new LoginIdentifier("owner-" + UUID.randomUUID() + "@example.com"),
                new PasswordHash("hash", "algo"), NOW);
        credentialAdapter.save(credential);
        return credential.userId();
    }

    @Test
    void savesAndRoundTripsAnAssignment() {
        UserId userId = persistedUserId();

        RoleAssignment saved = adapter.save(RoleAssignment.assign(
                userId, Role.TRAVELER, AssignedBy.service("auth-service"), NOW));

        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(adapter.findLatestByUserId(userId)).contains(saved);
    }

    @Test
    void latestAssignmentWinsAndHistoryIsPreserved() {
        UserId userId = persistedUserId();
        adapter.save(RoleAssignment.assign(userId, Role.TRAVELER, AssignedBy.service("auth-service"), NOW));
        adapter.save(RoleAssignment.assign(userId, Role.OPERATOR, AssignedBy.service("operator-service"),
                NOW.plusSeconds(60)));

        RoleAssignment latest = adapter.findLatestByUserId(userId).orElseThrow();
        assertThat(latest.role()).isEqualTo(Role.OPERATOR);
        assertThat(latest.assignedBy().value()).isEqualTo("service:operator-service");
    }

    @Test
    void findLatestIsEmptyForAUserWithNoAssignments() {
        assertThat(adapter.findLatestByUserId(UserId.generate())).isEmpty();
    }

    @Test
    void foreignKeyRejectsAssignmentsForNonexistentUsers() {
        assertThatThrownBy(() -> {
            adapter.save(RoleAssignment.assign(
                    UserId.generate(), Role.ADMIN, AssignedBy.service("auth-service"), NOW));
            testEntityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }
}
