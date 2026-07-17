package com.roadscanner.searchservice.application.usecase.search;

import com.roadscanner.searchservice.application.usecase.availability.AvailabilityOverlay;
import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.SortOption;
import com.roadscanner.searchservice.domain.port.in.SearchTrips;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import com.roadscanner.searchservice.domain.service.SearchRankingPolicy;

/**
 * Implements {@link SearchTrips} — the primary client-facing use case
 * (docs/services/search-service/use-cases.md). Resolves the query's sort to a concrete option
 * via {@link SearchRankingPolicy} exactly once, here, before the query ever reaches the
 * repository — the repository adapter has no default-sort rule of its own to keep in sync with
 * this one (see {@code SearchRankingPolicy}'s Javadoc on why the rule lives in exactly one
 * place).
 */
public class SearchTripsService implements SearchTrips {

    private final SearchableTripRepository repository;
    private final AvailabilityOverlay availabilityOverlay;
    private final SearchRankingPolicy rankingPolicy;

    public SearchTripsService(SearchableTripRepository repository, AvailabilityOverlay availabilityOverlay,
                              SearchRankingPolicy rankingPolicy) {
        this.repository = repository;
        this.availabilityOverlay = availabilityOverlay;
        this.rankingPolicy = rankingPolicy;
    }

    @Override
    public SearchTripsResult search(SearchTripsCommand command) {
        SearchQuery resolvedQuery = withResolvedSort(command.query());
        ResultPage<SearchableTrip> page = repository.search(resolvedQuery);

        var overlaid = page.content().stream().map(availabilityOverlay::overlay).toList();
        return new SearchTripsResult(page.withContent(overlaid));
    }

    private SearchQuery withResolvedSort(SearchQuery query) {
        SortOption resolvedSort = rankingPolicy.resolve(query.sort());
        if (resolvedSort == query.sort()) {
            return query;
        }
        return new SearchQuery(query.route(), query.travelDate(), query.minFare(), query.maxFare(),
                query.departureAfter(), query.departureBefore(), query.busTypeCategory(), query.minRating(),
                resolvedSort, query.page(), query.size());
    }
}
