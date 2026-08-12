package com.roadscanner.searchservice.domain.port.out;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;

import java.util.List;
import java.util.Map;

/**
 * Resolves live provider trips to the catalog trips that represent them.
 *
 * <p>Two things depend on this, and both are about identity rather than presentation. A provider
 * trip that catalog sync has already imported is <em>the same departure</em> as an indexed trip in
 * the same answer, and showing both would offer a traveller the same bus twice. And a provider trip
 * is identified only by {@code (providerCode, providerTripId)}, while every booking step downstream
 * is keyed by a catalog trip id — without the link there is nothing for a traveller to select.
 *
 * <p>Keyed on the provider's own trip id, never on operator, price, time or city name. Those
 * coincide across genuinely different departures; the provider trip id is the identity, and
 * inventory's mapping table is unique on it.
 *
 * <p><strong>Degrades, never fails.</strong> A trip that cannot be resolved is reported as absent
 * rather than raising: it means "not bookable through this platform yet", which is the honest state
 * for a provider departure catalog sync has not reached. Following
 * {@code InventoryAvailabilityClientAdapter}'s rule — a search is never worth failing over a
 * missing overlay.
 */
public interface CatalogTripResolver {

    /**
     * @return catalog trip id per provider trip id, omitting any that could not be resolved. Never
     * null, and never contains a fabricated id.
     */
    Map<String, java.util.UUID> resolveCatalogTripIds(List<ProviderTripResult> providerTrips);
}
