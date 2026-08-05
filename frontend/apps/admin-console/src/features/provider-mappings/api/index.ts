import { searchApi } from '@/lib/api/client';
import type {
  LocationSummary,
  ProviderMapping,
  ProviderMappingPage,
  ProviderMappingQuery,
  ProviderMappingRequest,
} from '../types';

/**
 * search-service — `location/adapter/in/rest/ProviderMappingController` and the catalogue's
 * autocomplete route.
 *
 * <p>Every provider-mapping route requires a JWT carrying `ROLE_ADMIN`, **including the reads**.
 * That is stricter than the rest of that service, whose location reads are public, because these
 * are the only responses that carry a provider's own identifiers.
 *
 * <p>This module is the only place in the app that knows these paths.
 */
export const providerMappingsApi = {
  /** GET /api/v1/provider-mappings — filtered, paged, most recently updated first. */
  async list(query: ProviderMappingQuery): Promise<ProviderMappingPage> {
    const { data } = await searchApi.get<ProviderMappingPage>('/api/v1/provider-mappings', {
      // Omitted rather than sent empty: the backend treats an absent parameter as "no filter",
      // and a blank string would be a filter that matches nothing on `provider`.
      params: {
        ...(query.provider ? { provider: query.provider } : {}),
        ...(query.verified === null ? {} : { verified: query.verified }),
        ...(query.search.trim() ? { q: query.search.trim() } : {}),
        page: query.page,
        size: query.size,
      },
    });
    return data;
  },

  /** GET /api/v1/provider-mappings/{id} */
  async get(id: string): Promise<ProviderMapping> {
    const { data } = await searchApi.get<ProviderMapping>(`/api/v1/provider-mappings/${id}`);
    return data;
  },

  /** POST /api/v1/provider-mappings — 409 when it would duplicate a mapping or reuse a provider id. */
  async create(body: ProviderMappingRequest): Promise<ProviderMapping> {
    const { data } = await searchApi.post<ProviderMapping>('/api/v1/provider-mappings', body);
    return data;
  },

  /** PUT /api/v1/provider-mappings/{id} — full replace; location and provider are ignored. */
  async update(id: string, body: ProviderMappingRequest): Promise<ProviderMapping> {
    const { data } = await searchApi.put<ProviderMapping>(`/api/v1/provider-mappings/${id}`, body);
    return data;
  },

  /** DELETE /api/v1/provider-mappings/{id} — a hard delete, and idempotent. */
  async remove(id: string): Promise<void> {
    await searchApi.delete(`/api/v1/provider-mappings/${id}`);
  },

  /**
   * GET /api/v1/provider-mappings/unmapped-locations — the onboarding worklist.
   *
   * <p>`provider` is required by the backend: "unmapped for whom?" has no default answer.
   */
  async unmappedLocations(
    provider: string,
    search: string,
    limit = 100,
  ): Promise<LocationSummary[]> {
    const { data } = await searchApi.get<{ locations: LocationSummary[] }>(
      '/api/v1/provider-mappings/unmapped-locations',
      {
        params: {
          provider,
          ...(search.trim() ? { q: search.trim() } : {}),
          limit,
        },
      },
    );
    return data.locations;
  },
};

/**
 * The canonical location catalogue, used only to *find* places that already exist.
 *
 * <p>Deliberately read-only. The catalogue is authored through `POST /api/v1/locations`, which
 * this console does not call: a mapping translates a place that already exists, and letting a
 * provider's vocabulary mint RoadScanner places would invert the direction the catalogue is
 * authored in. There is no create/update/delete function here to reach for by accident.
 */
export const locationsApi = {
  /** GET /api/v1/locations — prefix autocomplete over active catalogue entries (limit 1–25). */
  async search(term: string, limit = 10): Promise<LocationSummary[]> {
    const { data } = await searchApi.get<{ suggestions: LocationSummary[] }>('/api/v1/locations', {
      params: { q: term, limit },
    });
    return data.suggestions;
  },
};
