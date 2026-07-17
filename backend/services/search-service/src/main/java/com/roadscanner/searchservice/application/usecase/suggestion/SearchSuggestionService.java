package com.roadscanner.searchservice.application.usecase.suggestion;

import com.roadscanner.searchservice.domain.port.in.GetSearchSuggestions;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;

/**
 * Implements {@link GetSearchSuggestions} — autocomplete over place names already present in
 * the index (docs/services/search-service/use-cases.md). Thin by design: the repository's
 * {@code suggestPlaces} already does the prefix matching, deduplication, and limiting; this
 * service exists only to translate between the port's command/result shape and the repository
 * call.
 */
public class SearchSuggestionService implements GetSearchSuggestions {

    private final SearchableTripRepository repository;

    public SearchSuggestionService(SearchableTripRepository repository) {
        this.repository = repository;
    }

    @Override
    public SearchSuggestionsResult suggest(SearchSuggestionsCommand command) {
        return new SearchSuggestionsResult(repository.suggestPlaces(command.prefix(), command.maxResults()));
    }
}
