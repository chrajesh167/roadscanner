package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.LocationId;

import java.util.Objects;

/**
 * Soft-deletes a catalogue entry (admin) — the only removal path this module offers.
 *
 * <p>There is deliberately no hard delete. A location can be referenced by historical trips and
 * by provider mappings whose foreign key points at it, so the row must remain resolvable
 * permanently; withdrawing it from autocomplete is the actual intent behind "delete".
 */
public interface DisableLocation {

    DisableLocationResult disable(DisableLocationCommand command);

    record DisableLocationCommand(LocationId locationId) {
        public DisableLocationCommand {
            Objects.requireNonNull(locationId, "locationId must not be null");
        }
    }

    /**
     * @param alreadyDisabled true when the location was already inactive, so a retried DELETE is
     *                        reported honestly instead of pretending it changed something.
     * @throws com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException
     *         if no location with this id exists.
     */
    record DisableLocationResult(LocationId locationId, boolean alreadyDisabled) {
        public DisableLocationResult {
            Objects.requireNonNull(locationId, "locationId must not be null");
        }
    }
}
