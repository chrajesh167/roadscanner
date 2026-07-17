package com.roadscanner.searchservice.adapter.in.rest.suggestion;

import java.util.List;

/** Autocomplete suggestions — place names (origins and destinations) matching a prefix. */
public record SearchSuggestionsResponse(List<String> suggestions) {
}
