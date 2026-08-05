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

    /**
     * Active locations with no mapping for the given provider — the onboarding worklist.
     *
     * <p>A {@code NOT EXISTS} anti-join rather than {@code NOT IN}: the two are not equivalent
     * when the subquery can produce a NULL, and {@code NOT IN} would then return nothing at all.
     * {@code NOT EXISTS} is also the form Postgres can turn into an anti-join over
     * {@code uq_provider_location_mapping_location_provider}, so the cost stays proportional to
     * the page rather than to the product of the two tables.
     *
     * <p>The optional term matches anywhere in the name or city, not just as a prefix. This list
     * is an operator hunting for a place they already have in mind, unlike the traveller-facing
     * autocomplete above where prefix matching is what makes results predictable as you type.
     */
    @Query("""
            SELECT l FROM LocationJpaEntity l
            WHERE l.active = true
              AND NOT EXISTS (
                  SELECT 1 FROM ProviderLocationMappingJpaEntity m
                  WHERE m.locationId = l.id AND m.provider = :provider)
              AND (:pattern IS NULL
                   OR lower(l.displayName) LIKE :pattern ESCAPE '\\'
                   OR lower(l.city) LIKE :pattern ESCAPE '\\')
            ORDER BY l.displayName, l.id
            """)
    List<LocationJpaEntity> findActiveWithoutMappingForProvider(@Param("provider") String provider,
                                                                @Param("pattern") String pattern,
                                                                Pageable pageable);
}
