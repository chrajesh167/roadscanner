package com.roadscanner.searchservice.testsupport.fakes;

import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map-backed SearchableTripRepository for framework-free application-layer tests. {@link #search}
 * applies only route-equality and bookability filtering, deliberately not the full
 * filter/sort/pagination behavior — that combinatorial correctness is
 * {@code SearchableTripRepositoryAdapterTest}'s job (against real Postgres); this fake exists so
 * use-case tests can verify orchestration (sort-default resolution, availability overlay)
 * without depending on persistence at all.
 */
public final class InMemorySearchableTripRepository implements SearchableTripRepository {

    private final Map<TripId, SearchableTrip> byTripId = new ConcurrentHashMap<>();

    @Override
    public Optional<SearchableTrip> findByTripId(TripId tripId) {
        return Optional.ofNullable(byTripId.get(tripId));
    }

    @Override
    public ResultPage<SearchableTrip> search(SearchQuery query) {
        List<SearchableTrip> matches = byTripId.values().stream()
                .filter(SearchableTrip::bookable)
                .filter(trip -> trip.route().equals(query.route()))
                .toList();
        return ResultPage.of(matches, query.page(), query.size(), matches.size());
    }

    @Override
    public List<String> suggestPlaces(String prefix, int limit) {
        Set<String> matches = new LinkedHashSet<>();
        byTripId.values().stream()
                .filter(SearchableTrip::bookable)
                .forEach(trip -> {
                    if (trip.route().origin().toLowerCase().startsWith(prefix.toLowerCase())) {
                        matches.add(trip.route().origin());
                    }
                    if (trip.route().destination().toLowerCase().startsWith(prefix.toLowerCase())) {
                        matches.add(trip.route().destination());
                    }
                });
        return matches.stream().limit(limit).toList();
    }

    @Override
    public SearchableTrip save(SearchableTrip trip) {
        byTripId.put(trip.tripId(), trip);
        return trip;
    }

    @Override
    public void deleteAll() {
        byTripId.clear();
    }

    public boolean isEmpty() {
        return byTripId.isEmpty();
    }
}
