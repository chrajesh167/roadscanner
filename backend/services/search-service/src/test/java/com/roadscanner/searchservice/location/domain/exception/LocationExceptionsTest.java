package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The module's failures must stay diagnosable: each exception carries the identifier that caused
 * it, so a log line pins down the exact row without the handler having to re-parse a message.
 *
 * <p>Also pins the shared-root rule — every one of these extends {@code SearchServiceException},
 * which is what lets the single {@code GlobalExceptionHandler} map them rather than needing a
 * parallel handler for the location module.
 */
class LocationExceptionsTest {

    @Test
    void locationNotFoundCarriesTheIdThatWasNotFound() {
        LocationId id = LocationId.generate();

        LocationNotFoundException exception = new LocationNotFoundException(id);

        assertThat(exception).isInstanceOf(SearchServiceException.class);
        assertThat(exception.locationId()).isEqualTo(id);
        assertThat(exception).hasMessageContaining(id.toString());
    }

    @Test
    void duplicateGooglePlaceIdCarriesTheContestedPlaceId() {
        GooglePlaceId placeId = new GooglePlaceId("place-hyd");

        DuplicateGooglePlaceIdException exception = new DuplicateGooglePlaceIdException(placeId);

        assertThat(exception).isInstanceOf(SearchServiceException.class);
        assertThat(exception.googlePlaceId()).isEqualTo(placeId);
        assertThat(exception).hasMessageContaining("place-hyd");
    }

    @Test
    void providerMappingNotFoundCarriesBothTheLocationAndTheProvider() {
        LocationId id = LocationId.generate();
        ProviderCode provider = new ProviderCode("FLIXBUS");

        ProviderMappingNotFoundException exception = new ProviderMappingNotFoundException(id, provider);

        assertThat(exception).isInstanceOf(SearchServiceException.class);
        assertThat(exception.locationId()).isEqualTo(id);
        assertThat(exception.provider()).isEqualTo(provider);
        // Both halves matter: "which place" and "which provider" are separate questions.
        assertThat(exception).hasMessageContaining(id.toString()).hasMessageContaining("FLIXBUS");
    }
}
