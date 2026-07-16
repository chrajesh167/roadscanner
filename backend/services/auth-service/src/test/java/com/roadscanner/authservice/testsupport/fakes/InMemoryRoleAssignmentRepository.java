package com.roadscanner.authservice.testsupport.fakes;

import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** List-backed, append-only RoleAssignmentRepository for framework-free use-case tests. */
public final class InMemoryRoleAssignmentRepository implements RoleAssignmentRepository {

    private final List<RoleAssignment> assignments = new ArrayList<>();

    @Override
    public RoleAssignment save(RoleAssignment assignment) {
        assignments.add(assignment);
        return assignment;
    }

    @Override
    public Optional<RoleAssignment> findLatestByUserId(UserId userId) {
        return assignments.stream()
                .filter(assignment -> assignment.userId().equals(userId))
                .max(Comparator.comparing(RoleAssignment::assignedAt));
    }

    public List<RoleAssignment> all() {
        return List.copyOf(assignments);
    }
}
