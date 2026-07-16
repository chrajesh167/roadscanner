package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;

import java.util.UUID;

/** See {@link CredentialMapper}'s Javadoc for the domain/JPA boundary this class sits on. */
final class RoleAssignmentMapper {

    RoleAssignment toDomain(RoleAssignmentJpaEntity entity) {
        return new RoleAssignment(
                new UserId(entity.getUserId()),
                Role.valueOf(entity.getRole()),
                new AssignedBy(entity.getAssignedBy()),
                entity.getAssignedAt()
        );
    }

    /** The row id is a persistence detail with no domain counterpart — the domain identifies
     * an assignment by its content (an immutable fact), so the id is minted here. */
    RoleAssignmentJpaEntity toNewEntity(RoleAssignment assignment) {
        return new RoleAssignmentJpaEntity(
                UUID.randomUUID(),
                assignment.userId().value(),
                assignment.role().name(),
                assignment.assignedBy().value(),
                assignment.assignedAt()
        );
    }
}
