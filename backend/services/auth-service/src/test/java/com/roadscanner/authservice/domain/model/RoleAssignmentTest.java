package com.roadscanner.authservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAssignmentTest {

    @Test
    void assignCreatesAnAssignmentWithGivenFields() {
        UserId userId = new UserId(UUID.randomUUID());
        AssignedBy admin = AssignedBy.admin(new UserId(UUID.randomUUID()));
        Instant now = Instant.now();

        RoleAssignment assignment = RoleAssignment.assign(userId, Role.OPERATOR, admin, now);

        assertThat(assignment.userId()).isEqualTo(userId);
        assertThat(assignment.role()).isEqualTo(Role.OPERATOR);
        assertThat(assignment.assignedBy()).isEqualTo(admin);
        assertThat(assignment.assignedAt()).isEqualTo(now);
    }

    @Test
    void rejectsNullFields() {
        UserId userId = new UserId(UUID.randomUUID());
        AssignedBy admin = AssignedBy.service("operator-service");
        Instant now = Instant.now();

        assertThatThrownBy(() -> new RoleAssignment(null, Role.TRAVELER, admin, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoleAssignment(userId, null, admin, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoleAssignment(userId, Role.TRAVELER, null, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoleAssignment(userId, Role.TRAVELER, admin, null))
                .isInstanceOf(NullPointerException.class);
    }
}
