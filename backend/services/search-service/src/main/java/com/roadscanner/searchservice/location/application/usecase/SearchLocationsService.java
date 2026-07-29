package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.port.in.SearchLocations;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;

import java.util.List;

/**
 * Implements {@link SearchLocations}.
 *
 * <p>Thin by design: the prefix match, the active-only filter and the ordering all belong in the
 * query the adapter issues, not in memory here — the same division {@code SearchSuggestionService}
 * keeps with {@code SearchableTripRepository.suggestPlaces}. What this class owns is the one rule
 * that is not the database's business: clamping the caller's limit so a client cannot ask for the
 * entire catalogue in one autocomplete keystroke.
 */
public class SearchLocationsService implements SearchLocations {

    /** Upper bound regardless of what the caller asks for. */
    static final int MAX_LIMIT = 25;

    private final LocationRepository repository;

    public SearchLocationsService(LocationRepository repository) {
        this.repository = repository;
    }

    @Override
    public SearchLocationsResult search(SearchLocationsCommand command) {
        int effectiveLimit = Math.min(command.limit(), MAX_LIMIT);
        List<Location> locations = repository.searchActiveByPrefix(command.query(), effectiveLimit);
        return new SearchLocationsResult(locations);
    }
}
