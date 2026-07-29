package com.roadscanner.searchservice.location.adapter.out.googleplaces;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Google's Places Autocomplete wire format — only the fields this service actually reads.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} throughout, deliberately: Google adds
 * fields to this response over time, and an unrecognised one must not break autocomplete for
 * every user. Package-private, so Google's vocabulary cannot escape this adapter package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GoogleAutocompleteResponse(
        String status,
        List<Prediction> predictions,
        @JsonProperty("error_message") String errorMessage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Prediction(
            @JsonProperty("place_id") String placeId,
            String description,
            @JsonProperty("structured_formatting") StructuredFormatting structuredFormatting) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StructuredFormatting(
            @JsonProperty("main_text") String mainText,
            @JsonProperty("secondary_text") String secondaryText) {
    }
}
