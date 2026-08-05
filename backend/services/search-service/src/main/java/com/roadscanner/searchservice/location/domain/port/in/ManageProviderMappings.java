package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;

import java.util.Objects;

/**
 * Authoring the provider translation layer: create, update, delete.
 *
 * <p>Separate from {@link GetProviderMapping}, which stays exactly as it was — an in-process read
 * path for the integration boundary, deliberately absent from any client-facing contract. This
 * port is the administrative counterpart, and the split matters: provider identifiers must never
 * appear in a traveller-facing API, but an operator onboarding a provider has to see and type them.
 * Everything reachable through here is gated on {@code ROLE_ADMIN} at the REST layer, the same
 * posture {@code provider-integration-service}'s {@code ManageProviders} takes over its registry.
 *
 * <p>Nothing here creates a canonical {@link com.roadscanner.searchservice.location.domain.model.Location}.
 * A mapping is a translation of a place that already exists; letting a provider's vocabulary mint
 * RoadScanner places would invert the direction the catalogue is authored in, and is the one thing
 * this module exists to prevent.
 */
public interface ManageProviderMappings {

    /** Absent rather than thrown, so the REST layer decides the status code once. */
    java.util.Optional<ProviderLocationMapping> getById(ProviderLocationMappingId id);

    ProviderLocationMapping create(CreateCommand command);

    ProviderLocationMapping update(UpdateCommand command);

    /** Idempotent: deleting an already-deleted mapping is not an error, since the caller's intent
     * — that this translation should not exist — is satisfied either way. */
    void delete(ProviderLocationMappingId id);

    /**
     * The canonical location and the provider are supplied only here. Both are immutable
     * afterwards, which is why {@link UpdateCommand} carries neither.
     */
    record CreateCommand(LocationId locationId, ProviderCode provider, ProviderPlaceRef placeRef,
                         String metadataJson, boolean verified) {
        public CreateCommand {
            Objects.requireNonNull(locationId, "locationId must not be null");
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(placeRef, "placeRef must not be null");
        }
    }

    /**
     * A full replace of the editable fields.
     *
     * <p>Carries no location and no provider: those two identify which translation this is, and
     * changing either would silently turn one mapping into a different one, taking its verified
     * flag and sync history along with it. Re-pointing a mapping is a delete plus a create, which
     * is honest about what it does. The persistence layer enforces this independently —
     * {@code ProviderLocationMappingJpaEntity} declares both columns {@code updatable = false}.
     */
    record UpdateCommand(ProviderLocationMappingId id, ProviderPlaceRef placeRef, String metadataJson,
                         boolean verified) {
        public UpdateCommand {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(placeRef, "placeRef must not be null");
        }
    }
}
