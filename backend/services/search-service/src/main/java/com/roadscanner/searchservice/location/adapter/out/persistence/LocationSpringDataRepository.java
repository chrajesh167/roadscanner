package com.roadscanner.searchservice.location.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw Spring Data access, wrapped by {@link LocationRepositoryAdapter} before anything outside
 * this package touches it — the same containment {@code SearchableTripSpringDataRepository} keeps.
 */
interface LocationSpringDataRepository extends JpaRepository<LocationJpaEntity, UUID> {

    Optional<LocationJpaEntity> findByGooglePlaceId(String googlePlaceId);

    /**
     * Autocomplete: prefix match on either display name or city, active rows only.
     *
     * <p>Ordered so that a display-name hit outranks a city-only hit — typing "hyd" should surface
     * "Hyderabad" itself above a stop that merely sits in Hyderabad — then alphabetically for a
     * stable, predictable list.
     */
    @Query("""
            SELECT l FROM LocationJpaEntity l
            WHERE l.active = true
              AND (lower(l.displayName) LIKE lower(concat(:prefix, '%'))
                   OR lower(l.city) LIKE lower(concat(:prefix, '%')))
            ORDER BY
              CASE WHEN lower(l.displayName) LIKE lower(concat(:prefix, '%')) THEN 0 ELSE 1 END,
              l.displayName
            """)
    List<LocationJpaEntity> searchActiveByPrefix(@Param("prefix") String prefix, Pageable pageable);
}
