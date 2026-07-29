package com.roadscanner.searchservice.location.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Where a {@link Location} sits administratively: city, optional state/province, country.
 *
 * <p>Grouped into one value object rather than three loose strings because they are only
 * meaningful together — "Hyderabad" alone is ambiguous, "Hyderabad, Telangana, India" is not —
 * and because that keeps the required-ness rule (city and country required, state optional) in a
 * single place instead of restated at every call site, the same discipline
 * {@code Route} applies to origin/destination.
 */
public record LocationAddress(String city, String state, String country) {

    public LocationAddress {
        city = requireNonBlank(city, "city");
        country = requireNonBlank(country, "country");
        // State is genuinely absent in many countries — normalise blank to null so callers have
        // exactly one representation of "not applicable" to handle.
        state = (state == null || state.isBlank()) ? null : state.trim();
    }

    public Optional<String> stateIfPresent() {
        return Optional.ofNullable(state);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    @Override
    public String toString() {
        return state == null ? city + ", " + country : city + ", " + state + ", " + country;
    }
}
