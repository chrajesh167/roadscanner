package com.roadscanner.inventoryservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SyncRecordSpringDataRepository extends JpaRepository<SyncRecordJpaEntity, UUID> {

    Optional<SyncRecordJpaEntity> findFirstByProviderTypeOrderByLastAttemptAtDesc(String providerType);

    List<SyncRecordJpaEntity> findAllByOrderByLastAttemptAtDesc();
}
