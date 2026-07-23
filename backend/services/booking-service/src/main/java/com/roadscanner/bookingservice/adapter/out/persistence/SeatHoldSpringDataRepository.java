package com.roadscanner.bookingservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SeatHoldSpringDataRepository extends JpaRepository<SeatHoldJpaEntity, UUID> {

    Optional<SeatHoldJpaEntity> findByProviderBlockReference(String providerBlockReference);

    List<SeatHoldJpaEntity> findByExpiresAtBefore(Instant cutoff);
}
