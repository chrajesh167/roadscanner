package com.roadscanner.searchservice.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Raw Spring Data JPA access — framework-specific by design, wrapped by
 * {@link SearchableTripRepositoryAdapter} before anything outside this package touches it.
 * {@link JpaSpecificationExecutor} backs the dynamic filter/sort/pagination query built by
 * {@link SearchableTripSpecifications}.
 */
interface SearchableTripSpringDataRepository
        extends JpaRepository<SearchableTripJpaEntity, UUID>, JpaSpecificationExecutor<SearchableTripJpaEntity> {

    @Query("""
            SELECT DISTINCT e.origin FROM SearchableTripJpaEntity e
            WHERE e.bookable = true AND lower(e.origin) LIKE lower(concat(:prefix, '%'))
            ORDER BY e.origin
            """)
    List<String> findDistinctOriginsByPrefix(@Param("prefix") String prefix, Pageable pageable);

    @Query("""
            SELECT DISTINCT e.destination FROM SearchableTripJpaEntity e
            WHERE e.bookable = true AND lower(e.destination) LIKE lower(concat(:prefix, '%'))
            ORDER BY e.destination
            """)
    List<String> findDistinctDestinationsByPrefix(@Param("prefix") String prefix, Pageable pageable);
}
