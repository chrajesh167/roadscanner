package com.roadscanner.bookingservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BookingSpringDataRepository extends JpaRepository<BookingJpaEntity, UUID> {

    List<BookingJpaEntity> findByTravelerId(UUID travelerId);

    List<BookingJpaEntity> findByTripId(UUID tripId);

    List<BookingJpaEntity> findByTripIdAndStatusIn(UUID tripId, List<String> statuses);

    Optional<BookingJpaEntity> findByProviderBlockReference(String providerBlockReference);

    List<BookingJpaEntity> findByStatusAndTripDepartureTimeBefore(String status, Instant cutoff);

    boolean existsByTravelerIdAndTripIdAndStatus(UUID travelerId, UUID tripId, String status);

    List<BookingJpaEntity> findByStatusAndHoldExpiresAtBefore(String status, Instant cutoff);
}
