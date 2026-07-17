package com.roadscanner.searchservice.domain.port.out;

import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link SearchableTrip}. Implemented by a Postgres/JPA adapter in
 * {@code adapter.out.persistence}. Returns a domain {@link ResultPage}, never a Spring Data
 * {@code Page} or a JPA {@code Specification} — those are adapter-internal implementation
 * details of {@code search(SearchQuery)}, translated at the adapter boundary, per
 * docs/services/search-service/package-structure-equivalent discipline (the same
 * dependency-direction rule {@code auth-service}'s {@code domain/port/out} enforces: this
 * package depends on nothing outside the domain layer).
 */
public interface SearchableTripRepository {

    Optional<SearchableTrip> findByTripId(TripId tripId);

    /** Applies filters, sort, and pagination from {@code query} — see docs/services/search-service/domain-model.md.
     * {@code query.sort()} must already be resolved to a concrete option by the caller
     * ({@code SearchTripsService}, via {@code SearchRankingPolicy}) — this port does not itself
     * default a {@code null} sort, so that rule lives in exactly one place. */
    ResultPage<SearchableTrip> search(SearchQuery query);

    /** Distinct origin/destination place names starting with {@code prefix}, case-insensitive,
     * from currently bookable trips only — backs {@code GetSearchSuggestions}. */
    List<String> suggestPlaces(String prefix, int limit);

    SearchableTrip save(SearchableTrip trip);

    /** Discards the entire index — the first step of {@code RebuildIndex}
     * (docs/services/search-service/use-cases.md). Safe only because every row here is a
     * disposable, replayable copy (docs/architecture/database-ownership.md). */
    void deleteAll();
}
