package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;

import java.util.Optional;

/**
 * Persistence port for {@link RoleAssignment}. Append-only by design — assignments are
 * immutable facts (see {@link RoleAssignment}'s Javadoc), so there is no update or delete:
 * changing a user's role means saving a new assignment, and the current role is simply the
 * latest one. Implemented by a Postgres/JPA adapter in adapter.out.persistence, added
 * alongside the AssignRole use case per src/main/resources/db/migration/README.md.
 */
public interface RoleAssignmentRepository {

    RoleAssignment save(RoleAssignment assignment);

    /** The user's current role — the most recent assignment, per {@link RoleAssignment}'s Javadoc. */
    Optional<RoleAssignment> findLatestByUserId(UserId userId);
}
