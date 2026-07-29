package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves how a given location is expressed in a provider's own vocabulary.
 *
 * <p><strong>Not exposed over the public REST API, by design.</strong> Provider identifiers must
 * not leak into a client-facing contract — that is the whole point of this module. This port
 * exists for in-process callers at the integration boundary: Sprint 3's FlixBus mapping work
 * calls it to translate a RoadScanner location into provider ids immediately before a provider
 * request, and nowhere else.
 */
public interface GetProviderMapping {

    GetProviderMappingResult get(GetProviderMappingCommand command);

    /** All providers' mappings for one location. */
    ProviderMappingsResult getAll(LocationId locationId);

    record GetProviderMappingCommand(LocationId locationId, ProviderCode provider) {
        public GetProviderMappingCommand {
            Objects.requireNonNull(locationId, "locationId must not be null");
            Objects.requireNonNull(provider, "provider must not be null");
        }
    }

    /**
     * The mapping, if one exists. Absence is a legitimate answer — not every provider serves
     * every place — so this returns an {@code Optional} rather than throwing, and the caller
     * decides whether absence is fatal.
     */
    record GetProviderMappingResult(Optional<ProviderLocationMapping> mapping) {
        public GetProviderMappingResult {
            Objects.requireNonNull(mapping, "mapping must not be null");
        }
    }

    record ProviderMappingsResult(List<ProviderLocationMapping> mappings) {
        public ProviderMappingsResult {
            Objects.requireNonNull(mappings, "mappings must not be null");
            mappings = List.copyOf(mappings);
        }
    }
}
