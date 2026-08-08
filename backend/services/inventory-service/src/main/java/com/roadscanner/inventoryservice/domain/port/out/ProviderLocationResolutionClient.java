package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.ProviderType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Translates canonical RoadScanner locations into one provider's own city ids, by asking
 * {@code search-service} — which owns {@code provider_location_mapping}, the platform's single
 * mapping table.
 *
 * <p>This service deliberately holds no mapping data of its own. A second copy would be a second
 * thing to keep correct, and the failure mode of a stale copy is a trip imported against the wrong
 * city, discovered only when a traveller is turned away at boarding.
 *
 * <p>Degrades rather than throws, matching this service's other provider-facing port: a search
 * that cannot be translated is a route that is skipped this cycle, not a failed synchronisation.
 * The next cycle retries it.
 */
public interface ProviderLocationResolutionClient {

    /**
     * @return the provider's city id for each location it could be resolved for. Locations with no
     *         verified city mapping are simply absent — never guessed at, and never substituted
     *         with a name.
     */
    Map<UUID, String> resolveCityIds(ProviderType providerType, List<UUID> locationIds);
}
