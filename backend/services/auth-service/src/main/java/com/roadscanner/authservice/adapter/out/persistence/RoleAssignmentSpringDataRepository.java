package com.roadscanner.authservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Raw Spring Data JPA access — framework-specific by design, wrapped by
 * {@link RoleAssignmentRepositoryAdapter} before anything outside this package touches it.
 */
interface RoleAssignmentSpringDataRepository extends JpaRepository<RoleAssignmentJpaEntity, UUID> {

    Optional<RoleAssignmentJpaEntity> findFirstByUserIdOrderByAssignedAtDesc(UUID userId);
}
