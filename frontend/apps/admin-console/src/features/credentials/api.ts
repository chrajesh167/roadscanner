import { providerApi } from '@/lib/api/client';
import type { ProviderCredentialsRequest, ProviderCredentialsResponse } from '@/lib/api/types';

/**
 * provider-integration-service — the credential half of `ProviderAdminController`.
 *
 * <p>There is no read endpoint for credential *values* and this module does not pretend
 * otherwise. `get` returns presence and freshness; `store` returns the same summary after
 * writing. A secret enters this app in a form field and leaves in a request body — it is never
 * returned, never cached, and never stored client-side.
 */
export const credentialsApi = {
  /**
   * GET /api/v1/providers/{id}/credentials
   *
   * <p>404 means "no credentials stored for this provider", which is an ordinary state for a
   * newly registered provider rather than a failure. Callers distinguish it with
   * `ApiError.isNotFound`.
   */
  async get(providerId: string): Promise<ProviderCredentialsResponse> {
    const { data } = await providerApi.get<ProviderCredentialsResponse>(
      `/api/v1/providers/${providerId}/credentials`,
    );
    return data;
  },

  /**
   * PUT /api/v1/providers/{id}/credentials
   *
   * <p>A full replacement, not a patch: the backend takes the whole credential set, so omitting
   * a field clears it. 400 when neither a password nor a token is supplied — that rule lives in
   * the domain, and the form mirrors it so the admin hears about it before the round trip.
   */
  async store(
    providerId: string,
    body: ProviderCredentialsRequest,
  ): Promise<ProviderCredentialsResponse> {
    const { data } = await providerApi.put<ProviderCredentialsResponse>(
      `/api/v1/providers/${providerId}/credentials`,
      body,
    );
    return data;
  },
};
