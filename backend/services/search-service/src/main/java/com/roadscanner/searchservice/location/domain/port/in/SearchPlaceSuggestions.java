package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;

import java.util.List;
import java.util.Objects;

/**
 * Place autocomplete backed by an external provider, enriched with what the catalogue already
 * knows — the read path behind {@code GET /api/v1/google/places?q=}.
 *
 * <p>"Enriched" means exactly one thing: a suggestion whose Google place id already belongs to a
 * catalogue entry comes back carrying that entry's {@code LocationId}. Nothing is written. Google
 * cannot create a {@code Location}, and it certainly cannot create a provider mapping — those
 * remain, respectively, an admin decision and an independent concern.
 */
public interface SearchPlaceSuggestions {

    SearchPlaceSuggestionsResult search(SearchPlaceSuggestionsCommand command);

    /**
     * @param query the caller's fragment; blank is rejected here rather than billing the provider
     *              for a query that cannot match anything
     * @param limit maximum suggestions to return
     */
    record SearchPlaceSuggestionsCommand(String query, int limit) {
        public SearchPlaceSuggestionsCommand {
            Objects.requireNonNull(query, "query must not be null");
            query = query.trim();
            if (query.isEmpty()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be at least 1");
            }
        }
    }

    /**
     * @param cached true when this answer came from the cache rather than the provider. Surfaced
     *               so operators can see the hit rate on a paid API without instrumenting the
     *               adapter separately.
     * @throws com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteUnavailableException
     *         if the provider could not be reached. Never silently degraded to an empty list —
     *         see that exception's Javadoc.
     */
    record SearchPlaceSuggestionsResult(List<PlaceSuggestion> suggestions, boolean cached) {
        public SearchPlaceSuggestionsResult {
            Objects.requireNonNull(suggestions, "suggestions must not be null");
            suggestions = List.copyOf(suggestions);
        }
    }
}
