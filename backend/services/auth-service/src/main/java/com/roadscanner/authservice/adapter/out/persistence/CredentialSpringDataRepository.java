package com.roadscanner.authservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Raw Spring Data JPA access — framework-specific by design, wrapped by
 * {@link CredentialRepositoryAdapter} before anything outside this package touches it.
 */
interface CredentialSpringDataRepository extends JpaRepository<CredentialJpaEntity, UUID> {

    Optional<CredentialJpaEntity> findByLoginIdentifier(String loginIdentifier);

    boolean existsByLoginIdentifier(String loginIdentifier);
}
