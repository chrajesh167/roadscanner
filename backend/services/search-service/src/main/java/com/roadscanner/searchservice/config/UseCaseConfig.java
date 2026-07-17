package com.roadscanner.searchservice.config;

import com.roadscanner.searchservice.application.usecase.availability.AvailabilityOverlay;
import com.roadscanner.searchservice.application.usecase.detail.GetTripDetailService;
import com.roadscanner.searchservice.application.usecase.indexing.RatingSnapshotUpdater;
import com.roadscanner.searchservice.application.usecase.indexing.TripCancelledIndexer;
import com.roadscanner.searchservice.application.usecase.indexing.TripPublishedIndexer;
import com.roadscanner.searchservice.application.usecase.indexing.TripUpdatedIndexer;
import com.roadscanner.searchservice.application.usecase.rebuild.RebuildIndexService;
import com.roadscanner.searchservice.application.usecase.search.SearchTripsService;
import com.roadscanner.searchservice.application.usecase.suggestion.SearchSuggestionService;
import com.roadscanner.searchservice.domain.port.in.GetSearchSuggestions;
import com.roadscanner.searchservice.domain.port.in.GetTripDetail;
import com.roadscanner.searchservice.domain.port.in.IndexTripCancelled;
import com.roadscanner.searchservice.domain.port.in.IndexTripPublished;
import com.roadscanner.searchservice.domain.port.in.IndexTripUpdated;
import com.roadscanner.searchservice.domain.port.in.RebuildIndex;
import com.roadscanner.searchservice.domain.port.in.SearchTrips;
import com.roadscanner.searchservice.domain.port.in.UpdateRatingSnapshot;
import com.roadscanner.searchservice.domain.port.out.AvailabilityCache;
import com.roadscanner.searchservice.domain.port.out.AvailabilityClient;
import com.roadscanner.searchservice.domain.port.out.IndexReplayTrigger;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import com.roadscanner.searchservice.domain.service.SearchRankingPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit bean wiring for the domain policy and every application-layer use case. The
 * application classes carry no Spring stereotype annotations — they are plain constructors
 * wired here, keeping that layer framework-light and making every dependency of every use case
 * visible in one place, matching {@code auth-service}'s identical {@code UseCaseConfig}.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public SearchRankingPolicy searchRankingPolicy() {
        return new SearchRankingPolicy();
    }

    @Bean
    public AvailabilityOverlay availabilityOverlay(AvailabilityCache cache, AvailabilityClient client) {
        return new AvailabilityOverlay(cache, client);
    }

    @Bean
    public SearchTrips searchTrips(SearchableTripRepository repository, AvailabilityOverlay availabilityOverlay,
                                   SearchRankingPolicy rankingPolicy) {
        return new SearchTripsService(repository, availabilityOverlay, rankingPolicy);
    }

    @Bean
    public GetTripDetail getTripDetail(SearchableTripRepository repository, AvailabilityOverlay availabilityOverlay) {
        return new GetTripDetailService(repository, availabilityOverlay);
    }

    @Bean
    public GetSearchSuggestions getSearchSuggestions(SearchableTripRepository repository) {
        return new SearchSuggestionService(repository);
    }

    @Bean
    public IndexTripPublished indexTripPublished(SearchableTripRepository repository) {
        return new TripPublishedIndexer(repository);
    }

    @Bean
    public IndexTripUpdated indexTripUpdated(SearchableTripRepository repository) {
        return new TripUpdatedIndexer(repository);
    }

    @Bean
    public IndexTripCancelled indexTripCancelled(SearchableTripRepository repository) {
        return new TripCancelledIndexer(repository);
    }

    @Bean
    public UpdateRatingSnapshot updateRatingSnapshot(SearchableTripRepository repository) {
        return new RatingSnapshotUpdater(repository);
    }

    @Bean
    public RebuildIndex rebuildIndex(SearchableTripRepository repository, IndexReplayTrigger replayTrigger) {
        return new RebuildIndexService(repository, replayTrigger);
    }
}
