import { providerApi } from '@/lib/api/client';
import type { ProviderHealthResponse } from '@/lib/api/types';

/**
 * provider-integration-service — `adapter/in/rest/health/ProviderHealthController`.
 *
 * <p><strong>This route performs a live probe.</strong> It is not a read of the stored health
 * record: `CheckProviderHealth` calls the provider, records the outcome, and returns it. Polling
 * it on a timer would mean this console generating traffic against a partner's API for as long as
 * a browser tab is open, which is why nothing here runs on an interval — every probe is the
 * result of someone asking for one.
 *
 * <p>The stored record that `ProviderHealthMonitor` maintains on a schedule has no read-only
 * endpoint today. When one exists, the health screen should read that on load and keep this route
 * for the explicit "probe now" action.
 *
 * <p>Addressed by provider *code*, not registry id — this route predates the admin API and is
 * keyed on `ProviderType`.
 */
export const healthApi = {
  /** GET /internal/api/v1/providers/{providerType}/health */
  async probe(providerCode: string): Promise<ProviderHealthResponse> {
    const { data } = await providerApi.get<ProviderHealthResponse>(
      `/internal/api/v1/providers/${providerCode}/health`,
    );
    return data;
  },
};
