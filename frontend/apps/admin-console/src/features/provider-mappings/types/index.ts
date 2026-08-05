/**
 * Wire types for `search-service`'s location catalogue and provider translation layer.
 *
 * <p>Each interface mirrors a Java record one-for-one — same field names, same nullability,
 * nothing invented. They live in the feature rather than in `lib/api/types.ts` because that module
 * is documented as covering `auth-service` and `provider-integration-service`, and this is the only
 * part of the console that speaks to `search-service`.
 *
 * Sources:
 *   search-service  location/adapter/in/rest/{ProviderMappingResponse, ProviderMappingRequest,
 *                                             ProviderMappingPageResponse, LocationSummary}
 */

/**
 * `ProviderMappingResponse` — how one canonical location is expressed in one provider's
 * vocabulary, with the location it translates already resolved.
 *
 * <p>This is the only shape in the platform that carries a provider's own identifiers, and it is
 * reachable exclusively under `ROLE_ADMIN`. Nothing traveller-facing returns these values.
 */
export interface ProviderMapping {
  id: string;
  provider: string;

  locationId: string;
  locationDisplayName: string;
  locationCity: string;

  /** All three are individually optional: providers model geography inconsistently. */
  providerCityId: string | null;
  providerStationId: string | null;
  providerStationName: string | null;

  /** Opaque provider payload, returned exactly as stored. Never parsed by this console. */
  providerMetadata: string | null;

  verified: boolean;

  /** When last refreshed from the provider; null if never. */
  lastSynced: string | null;

  createdAt: string;
  updatedAt: string;
}

/** `ProviderMappingPageResponse`. Totals come from the same query as the rows. */
export interface ProviderMappingPage {
  mappings: ProviderMapping[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

/**
 * `ProviderMappingRequest`.
 *
 * <p>`locationId` and `provider` are required on create and **ignored on update** — together they
 * identify which translation this is. Re-pointing a mapping is a delete plus a create.
 */
export interface ProviderMappingRequest {
  locationId?: string;
  provider?: string;
  providerCityId?: string | null;
  providerStationId?: string | null;
  providerStationName?: string | null;
  providerMetadata?: string | null;
  verified: boolean;
}

/** `LocationSummary` — the compact catalogue shape, from autocomplete and the unmapped worklist. */
export interface LocationSummary {
  id: string;
  displayName: string;
  city: string;
  state: string | null;
  country: string;
}

/** Query parameters for the administrative listing. All filters combine with AND. */
export interface ProviderMappingQuery {
  provider: string | null;
  verified: boolean | null;
  search: string;
  page: number;
  size: number;
}
