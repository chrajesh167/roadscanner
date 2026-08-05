package com.roadscanner.searchservice.location.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw Spring Data access for provider mappings. Every finder here is backed by an index from V2 or
 * V5 ({@code uq_provider_location_mapping_location_provider},
 * {@code uq_provider_location_mapping_city}, {@code uq_provider_location_mapping_station},
 * {@code idx_provider_location}, {@code idx_provider_name}).
 */
interface ProviderLocationMappingSpringDataRepository
        extends JpaRepository<ProviderLocationMappingJpaEntity, UUID> {

    Optional<ProviderLocationMappingJpaEntity> findByLocationIdAndProvider(UUID locationId, String provider);

    List<ProviderLocationMappingJpaEntity> findByLocationId(UUID locationId);

    Optional<ProviderLocationMappingJpaEntity> findByProviderAndProviderCityId(String provider, String providerCityId);

    Optional<ProviderLocationMappingJpaEntity> findByProviderAndProviderStationId(String provider,
                                                                                 String providerStationId);

    /**
     * The administrative listing.
     *
     * <p>Each filter is written as "parameter is null OR it matches", so one query serves all eight
     * combinations of the three optional filters. A {@code Specification} tree would build the same
     * predicates dynamically at the cost of scattering them across several classes; with a fixed,
     * small filter set the single statement is easier to read and to keep aligned with its indexes.
     *
     * <p>The join to {@link LocationJpaEntity} is written on the id rather than through a mapped
     * association, because the entity deliberately holds {@code locationId} as a bare UUID to stop
     * callers traversing between two aggregates the domain keeps independent. The join lives here,
     * in one query, purely so the search term can reach the location's display name and city.
     *
     * <p>Ordering is total — most recently updated first, then id. Sorting on {@code updatedAt}
     * alone would leave ties resolved by whatever order the scan produced, so two identical
     * requests could return the same rows in a different arrangement, which is precisely what
     * pagination cannot tolerate.
     */
    /**
     * <p>The caller passes a ready-made {@code %term%} pattern, already lower-cased, rather than a
     * bare term this query wraps in {@code lower(concat(...))}. Two reasons, one of them a bug that
     * bit during development: a null parameter fed straight into {@code lower()} gives Postgres
     * nothing to infer a type from, so it binds as {@code bytea} and the statement dies with
     * "function lower(bytea) does not exist". Compared against a text column instead, the type is
     * unambiguous. It is also less work per row — the pattern is lower-cased once in Java rather
     * than once per candidate row.
     *
     * <p>The explicit {@code ESCAPE '\'} is what makes that escaping mean anything. Without it
     * Hibernate emits {@code like ? escape ''}, which switches escaping off entirely, and the
     * adapter's {@code \_} then has to match a literal backslash — so a term containing an
     * underscore found nothing at all rather than matching too much.
     */
    @Query("""
            SELECT m FROM ProviderLocationMappingJpaEntity m
            JOIN LocationJpaEntity l ON l.id = m.locationId
            WHERE (:provider IS NULL OR m.provider = :provider)
              AND (:verified IS NULL OR m.verified = :verified)
              AND (:pattern IS NULL
                   OR lower(l.displayName) LIKE :pattern ESCAPE '\\'
                   OR lower(l.city) LIKE :pattern ESCAPE '\\'
                   OR lower(m.providerCityId) LIKE :pattern ESCAPE '\\'
                   OR lower(m.providerStationId) LIKE :pattern ESCAPE '\\'
                   OR lower(m.providerStationName) LIKE :pattern ESCAPE '\\')
            ORDER BY m.updatedAt DESC, m.id
            """)
    Page<ProviderLocationMappingJpaEntity> search(@Param("provider") String provider,
                                                  @Param("verified") Boolean verified,
                                                  @Param("pattern") String pattern,
                                                  Pageable pageable);
}
