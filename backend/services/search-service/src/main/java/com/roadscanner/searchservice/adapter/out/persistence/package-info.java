/**
 * JPA/Postgres implementation of {@link com.roadscanner.searchservice.domain.port.out.SearchableTripRepository}.
 *
 * Four kinds of classes live here, each aware of only one side of the domain/JPA boundary:
 * <ul>
 *   <li>{@code SearchableTripJpaEntity} — persistence shape only, zero {@code domain.model} imports.</li>
 *   <li>{@code SearchableTripSpringDataRepository} — raw Spring Data JPA access, package-private.</li>
 *   <li>{@code SearchableTripSpecifications} — dynamic filter-query building from primitive/JPA-safe
 *       values only, zero {@code domain.model} imports (see its own Javadoc).</li>
 *   <li>{@code SearchableTripMapper} — the only class that sees both {@code domain.model} and the entity.</li>
 *   <li>{@code SearchableTripRepositoryAdapter} — implements the domain port, package-private.</li>
 * </ul>
 */
package com.roadscanner.searchservice.adapter.out.persistence;
