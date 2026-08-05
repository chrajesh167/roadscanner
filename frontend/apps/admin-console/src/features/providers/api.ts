import { providerApi } from '@/lib/api/client';
import type {
  ProviderHealthResponse,
  ProviderRequest,
  ProviderResponse,
  ProviderSessionSummaryResponse,
} from '@/lib/api/types';

/**
 * provider-integration-service — `adapter/in/rest/admin/ProviderAdminController`.
 *
 * Every route requires a JWT carrying `ROLE_ADMIN`; `SecurityConfig` rejects anything else with a
 * 403. This module is the only place in the app that knows these paths.
 */
export const providersApi = {
  /** GET /api/v1/providers */
  async list(enabledOnly = false): Promise<ProviderResponse[]> {
    const { data } = await providerApi.get<ProviderResponse[]>('/api/v1/providers', {
      params: { enabledOnly },
    });
    return data;
  },

  /** GET /api/v1/providers/{id} */
  async get(id: string): Promise<ProviderResponse> {
    const { data } = await providerApi.get<ProviderResponse>(`/api/v1/providers/${id}`);
    return data;
  },

  /** POST /api/v1/providers — always lands disabled, whatever the caller intends. */
  async register(body: ProviderRequest): Promise<ProviderResponse> {
    const { data } = await providerApi.post<ProviderResponse>('/api/v1/providers', body);
    return data;
  },

  /** PUT /api/v1/providers/{id} — full replace of the editable fields; `code` is ignored. */
  async update(id: string, body: ProviderRequest): Promise<ProviderResponse> {
    const { data } = await providerApi.put<ProviderResponse>(`/api/v1/providers/${id}`, body);
    return data;
  },

  /** POST /api/v1/providers/{id}/enable — idempotent. */
  async enable(id: string): Promise<ProviderResponse> {
    const { data } = await providerApi.post<ProviderResponse>(`/api/v1/providers/${id}/enable`);
    return data;
  },

  /** POST /api/v1/providers/{id}/disable — idempotent, and not a delete. */
  async disable(id: string): Promise<ProviderResponse> {
    const { data } = await providerApi.post<ProviderResponse>(`/api/v1/providers/${id}/disable`);
    return data;
  },

  /**
   * POST /api/v1/providers/{id}/test — runs a live probe against the provider and returns the
   * resulting durable health record. 502 when the provider could not be reached.
   */
  async test(id: string): Promise<ProviderHealthResponse> {
    const { data } = await providerApi.post<ProviderHealthResponse>(`/api/v1/providers/${id}/test`);
    return data;
  },

  /**
   * POST /api/v1/providers/{id}/refresh-session — authenticates afresh with the stored
   * credentials. 502 when the provider rejects them, which is the point: this is how an admin
   * proves a newly rotated secret works before enabling the provider.
   */
  async refreshSession(id: string): Promise<ProviderSessionSummaryResponse> {
    const { data } = await providerApi.post<ProviderSessionSummaryResponse>(
      `/api/v1/providers/${id}/refresh-session`,
    );
    return data;
  },
};
