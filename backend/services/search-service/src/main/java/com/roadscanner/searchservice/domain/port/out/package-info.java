/**
 * Outbound ports the domain depends on without owning the implementation:
 * {@link com.roadscanner.searchservice.domain.port.out.SearchableTripRepository},
 * {@link com.roadscanner.searchservice.domain.port.out.AvailabilityClient},
 * {@link com.roadscanner.searchservice.domain.port.out.AvailabilityCache}, and
 * {@link com.roadscanner.searchservice.domain.port.out.IndexReplayTrigger}.
 *
 * Interfaces only. Implementations (JPA/Postgres, the inventory-service HTTP client, Redis,
 * Kafka consumer seeking) live in {@code adapter.out.*}.
 */
package com.roadscanner.searchservice.domain.port.out;
