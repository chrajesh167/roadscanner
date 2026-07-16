package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Implements the {@link RoleAssignmentRepository} domain port over Postgres via JPA. Unlike
 * {@link CredentialRepositoryAdapter}, {@link #save} never fetches-then-mutates: assignments
 * are append-only, immutable facts, so every save is a genuine insert and there is no
 * optimistic-locking concern to work around.
 */
@Repository
class RoleAssignmentRepositoryAdapter implements RoleAssignmentRepository {

    private final RoleAssignmentSpringDataRepository springDataRepository;
    private final RoleAssignmentMapper mapper = new RoleAssignmentMapper();

    RoleAssignmentRepositoryAdapter(RoleAssignmentSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public RoleAssignment save(RoleAssignment assignment) {
        return mapper.toDomain(springDataRepository.save(mapper.toNewEntity(assignment)));
    }

    @Override
    public Optional<RoleAssignment> findLatestByUserId(UserId userId) {
        return springDataRepository.findFirstByUserIdOrderByAssignedAtDesc(userId.value()).map(mapper::toDomain);
    }
}
