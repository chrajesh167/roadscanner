package com.roadscanner.searchservice.location.domain.model;

/**
 * The provider's own view of a place: its city id, its station id, and the station name it prints
 * on a ticket. All three are optional individually — providers model geography inconsistently, and
 * some expose only a city while others expose only stations — but a mapping that carries none of
 * them identifies nothing, which this type enforces.
 *
 * <p>This is the containment boundary for provider vocabulary. Nothing outside
 * {@link ProviderLocationMapping} and its persistence adapter is permitted to read these values,
 * which is what makes "no service knows provider IDs" enforceable rather than aspirational.
 */
public record ProviderPlaceRef(String cityId, String stationId, String stationName) {

    private static final int MAX_LENGTH = 255;

    public ProviderPlaceRef {
        cityId = normalise(cityId, "providerCityId");
        stationId = normalise(stationId, "providerStationId");
        stationName = normalise(stationName, "providerStationName");

        if (cityId == null && stationId == null) {
            throw new IllegalArgumentException(
                    "a provider mapping must carry at least a providerCityId or a providerStationId");
        }
    }

    public boolean identifiesStation() {
        return stationId != null;
    }

    private static String normalise(String value, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
