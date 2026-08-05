package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;

/**
 * A create or update would have broken one of the mapping table's three uniqueness rules.
 *
 * <p>Carries {@link Conflict} rather than a single message because the three rules fail for
 * genuinely different reasons and an administrator needs to know which: a location already mapped
 * to this provider is fixed by editing the existing mapping, while a provider city id already in
 * use means the id belongs to a different place and the value itself is wrong. Collapsing them
 * into one "duplicate mapping" message would leave the operator guessing which field to change.
 *
 * <p>Checked in the application layer before writing, so the caller gets a precise 409 with a
 * field name rather than a raw constraint violation surfacing as a 500. V5's unique indexes remain
 * the real guarantee — the check exists for the error message, not for correctness under
 * concurrency, exactly as {@link DuplicateGooglePlaceIdException} does for Google place ids.
 */
public final class DuplicateProviderMappingException extends SearchServiceException {

    /** Which rule was broken. The field name matches the REST request property, so the API layer
     * can attach the message to the offending input without a translation table. */
    public enum Conflict {
        LOCATION_ALREADY_MAPPED("locationId"),
        PROVIDER_CITY_ID_IN_USE("providerCityId"),
        PROVIDER_STATION_ID_IN_USE("providerStationId");

        private final String field;

        Conflict(String field) {
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    private final Conflict conflict;
    private final ProviderCode provider;
    private final String conflictingValue;

    public DuplicateProviderMappingException(Conflict conflict, ProviderCode provider, String conflictingValue) {
        super(describe(conflict, provider, conflictingValue));
        this.conflict = conflict;
        this.provider = provider;
        this.conflictingValue = conflictingValue;
    }

    private static String describe(Conflict conflict, ProviderCode provider, String value) {
        return switch (conflict) {
            case LOCATION_ALREADY_MAPPED ->
                    "This location already has a " + provider + " mapping — edit that mapping instead";
            case PROVIDER_CITY_ID_IN_USE ->
                    provider + " city id " + value + " is already mapped to a different location";
            case PROVIDER_STATION_ID_IN_USE ->
                    provider + " station id " + value + " is already mapped to a different location";
        };
    }

    public Conflict conflict() {
        return conflict;
    }

    public ProviderCode provider() {
        return provider;
    }

    /** Null for {@link Conflict#LOCATION_ALREADY_MAPPED}, whose conflicting value is the location
     * id already named in the request. */
    public String conflictingValue() {
        return conflictingValue;
    }
}
