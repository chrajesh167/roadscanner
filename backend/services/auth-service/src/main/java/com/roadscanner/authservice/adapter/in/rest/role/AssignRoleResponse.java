package com.roadscanner.authservice.adapter.in.rest.role;

import com.roadscanner.authservice.domain.model.RoleAssignment;

import java.time.Instant;

public record AssignRoleResponse(
        String userId,
        String role,
        String assignedBy,
        Instant assignedAt
) {

    static AssignRoleResponse from(RoleAssignment assignment) {
        return new AssignRoleResponse(
                assignment.userId().toString(),
                assignment.role().name(),
                assignment.assignedBy().value(),
                assignment.assignedAt()
        );
    }
}
