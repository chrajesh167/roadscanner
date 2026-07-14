package com.roadscanner.authservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw Spring Data JPA access — framework-specific by design, wrapped by
 * {@link RefreshTokenRepositoryAdapter} before anything outside this package touches it.
 */
interface RefreshTokenSpringDataRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenJpaEntity> findByUserId(UUID userId);
}
