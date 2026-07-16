package com.roadscanner.authservice.application.usecase.role;

import com.roadscanner.authservice.domain.exception.UserNotFoundException;
import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.AssignRole;
import com.roadscanner.authservice.domain.port.out.StubPasswordHasher;
import com.roadscanner.authservice.testsupport.fakes.InMemoryCredentialRepository;
import com.roadscanner.authservice.testsupport.fakes.InMemoryRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignRoleServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    private final InMemoryCredentialRepository credentialRepository = new InMemoryCredentialRepository();
    private final InMemoryRoleAssignmentRepository roleAssignmentRepository = new InMemoryRoleAssignmentRepository();
    private final AssignRoleService service = new AssignRoleService(credentialRepository, roleAssignmentRepository);

    private UserId userId;

    @BeforeEach
    void registerUser() {
        Credential credential = Credential.register(UserId.generate(),
                new LoginIdentifier("traveler@example.com"),
                new StubPasswordHasher().hash("some-password-1x"), NOW);
        credentialRepository.save(credential);
        userId = credential.userId();
        roleAssignmentRepository.save(RoleAssignment.assign(
                userId, Role.TRAVELER, AssignedBy.service("auth-service"), NOW));
    }

    @Test
    void appendsANewAssignmentAndMakesItTheCurrentRole() {
        AssignRole.AssignRoleResult result = service.assign(new AssignRole.AssignRoleCommand(
                userId, Role.OPERATOR, AssignedBy.service("operator-service"), NOW.plusSeconds(60)));

        assertThat(result.assignment().role()).isEqualTo(Role.OPERATOR);
        // Append-only: history is preserved, the latest wins.
        assertThat(roleAssignmentRepository.all()).hasSize(2);
        assertThat(roleAssignmentRepository.findLatestByUserId(userId).orElseThrow().role())
                .isEqualTo(Role.OPERATOR);
    }

    @Test
    void rejectsAnUnknownUser() {
        UserId unknown = UserId.generate();

        assertThatThrownBy(() -> service.assign(new AssignRole.AssignRoleCommand(
                unknown, Role.ADMIN, AssignedBy.admin(userId), NOW)))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(roleAssignmentRepository.all()).hasSize(1);
    }
}
