package com.roadscanner.searchservice.domain.port.out;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;

/**
 * The synchronous call to {@code inventory-service}'s "Trip Availability Query" category
 * (docs/architecture/api-inventory.md), scoped narrowly to a seat count — never seat-level
 * detail, never a hold (docs/services/search-service/boundaries.md). Implemented in
 * {@code adapter.out.client}, sitting behind {@link AvailabilityCache} (a separate port, the
 * same split {@code auth-service} draws between {@code TokenSigner} and {@code RevocationCache}
 * — one port per infrastructure concern, even when an adapter composes both).
 *
 * Must never throw for an ordinary failure (timeout, connection refused, 5xx) — per
 * docs/services/search-service/boundaries.md's "degrade, not fail" rule, an implementation
 * catches those and returns {@link AvailabilityStatus#unknown()} instead. This is a deliberate
 * asymmetry with {@code auth-service}'s ports, which are allowed to throw because their
 * failures are business-meaningful; a failed availability lookup here is never business-
 * meaningful, only a display nicety withheld.
 */
public interface AvailabilityClient {

    AvailabilityStatus fetchAvailability(TripId tripId);
}
