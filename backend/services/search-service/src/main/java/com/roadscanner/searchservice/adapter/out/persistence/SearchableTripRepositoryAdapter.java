package com.roadscanner.searchservice.adapter.out.persistence;

import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.SortOption;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Implements the {@link SearchableTripRepository} domain port over Postgres via JPA.
 * Package-private — consumers depend on the {@link SearchableTripRepository} interface only,
 * matching {@code auth-service}'s {@code CredentialRepositoryAdapter} convention.
 *
 * {@link #save} fetches-then-mutates rather than always constructing a fresh entity, for the
 * identical optimistic-locking reason {@code CredentialRepositoryAdapter}'s Javadoc explains:
 * an unconditional fresh-entity save would hand Hibernate no {@code @Version} value read from
 * the database, silently bypassing the check that guards against two concurrently-processed
 * Kafka events for the same trip (e.g. a redelivered {@code TripUpdated} racing a
 * {@code ReviewSubmitted}) clobbering each other.
 */
@Repository
class SearchableTripRepositoryAdapter implements SearchableTripRepository {

    private final SearchableTripSpringDataRepository springDataRepository;
    private final SearchableTripMapper mapper = new SearchableTripMapper();

    SearchableTripRepositoryAdapter(SearchableTripSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<SearchableTrip> findByTripId(TripId tripId) {
        return springDataRepository.findById(tripId.value()).map(mapper::toDomain);
    }

    @Override
    public ResultPage<SearchableTrip> search(SearchQuery query) {
        Specification<SearchableTripJpaEntity> specification = buildSpecification(query);
        Pageable pageable = PageRequest.of(query.page(), query.size(), resolveSort(query.sort()));

        var page = springDataRepository.findAll(specification, pageable);
        List<SearchableTrip> content = page.getContent().stream().map(mapper::toDomain).toList();
        return ResultPage.of(content, query.page(), query.size(), page.getTotalElements());
    }

    @Override
    public List<String> suggestPlaces(String prefix, int limit) {
        Pageable topN = PageRequest.of(0, limit);
        Set<String> suggestions = new LinkedHashSet<>(
                springDataRepository.findDistinctOriginsByPrefix(prefix, topN));
        if (suggestions.size() < limit) {
            suggestions.addAll(springDataRepository.findDistinctDestinationsByPrefix(prefix, topN));
        }
        return suggestions.stream().limit(limit).toList();
    }

    @Override
    public SearchableTrip save(SearchableTrip trip) {
        SearchableTripJpaEntity entity = springDataRepository.findById(trip.tripId().value())
                .map(existing -> {
                    mapper.applyTo(existing, trip);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(trip));

        return mapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public void deleteAll() {
        springDataRepository.deleteAll();
    }

    private Specification<SearchableTripJpaEntity> buildSpecification(SearchQuery query) {
        List<Specification<SearchableTripJpaEntity>> specifications = new ArrayList<>();
        specifications.add(SearchableTripSpecifications.bookable());
        specifications.add(SearchableTripSpecifications.route(query.route().origin(), query.route().destination()));

        Instant dayStartUtc = query.travelDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEndUtc = query.travelDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        specifications.add(SearchableTripSpecifications.departingOn(dayStartUtc, dayEndUtc));

        if (query.minFare() != null) {
            specifications.add(SearchableTripSpecifications.minFare(query.minFare()));
        }
        if (query.maxFare() != null) {
            specifications.add(SearchableTripSpecifications.maxFare(query.maxFare()));
        }
        if (query.departureAfter() != null) {
            specifications.add(SearchableTripSpecifications.departureAfter(query.departureAfter()));
        }
        if (query.departureBefore() != null) {
            specifications.add(SearchableTripSpecifications.departureBefore(query.departureBefore()));
        }
        if (query.busTypeCategory() != null) {
            specifications.add(SearchableTripSpecifications.busTypeCategory(query.busTypeCategory()));
        }
        if (query.minRating() != null) {
            specifications.add(SearchableTripSpecifications.minRating(query.minRating()));
        }
        return SearchableTripSpecifications.allOf(specifications);
    }

    /**
     * {@code sort} must already be resolved to a concrete option by the caller —
     * {@code SearchTripsService} always does this via {@code SearchRankingPolicy} before
     * reaching this adapter, so the "no option specified" default lives in exactly one place
     * (that policy), not duplicated here as well.
     */
    private Sort resolveSort(SortOption sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "fareAmount");
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "fareAmount");
            case DEPARTURE_TIME_ASC -> Sort.by(Sort.Direction.ASC, "departureTime");
            case DURATION_ASC -> Sort.by(Sort.Direction.ASC, "durationSeconds");
            case RATING_DESC -> Sort.by(Sort.Direction.DESC, "ratingAverage");
        };
    }
}
