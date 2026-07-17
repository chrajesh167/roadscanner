package com.roadscanner.searchservice.domain.port.in;

import java.util.List;
import java.util.Objects;

/**
 * Autocomplete over place names already present in the index (origins and destinations of
 * indexed, bookable trips) — a lightweight aid to composing a {@code SearchQuery}, not a
 * separate data source of its own. {@code maxResults} is supplied by the caller (bounded by
 * configuration at the REST layer, per docs/services/search-service/api-summary.md), the same
 * "domain enforces the shape, configuration enforces the baseline" split
 * {@link com.roadscanner.searchservice.domain.model.SearchQuery} uses for page size.
 */
public interface GetSearchSuggestions {

    SearchSuggestionsResult suggest(SearchSuggestionsCommand command);

    record SearchSuggestionsCommand(String prefix, int maxResults) {
        public SearchSuggestionsCommand {
            Objects.requireNonNull(prefix, "prefix must not be null");
            prefix = prefix.trim();
            if (prefix.isEmpty()) {
                throw new IllegalArgumentException("prefix must not be blank");
            }
            if (maxResults < 1) {
                throw new IllegalArgumentException("maxResults must be positive");
            }
        }
    }

    record SearchSuggestionsResult(List<String> suggestions) {
        public SearchSuggestionsResult {
            Objects.requireNonNull(suggestions, "suggestions must not be null");
            suggestions = List.copyOf(suggestions);
        }
    }
}
