import { PROVIDER_CAPABILITIES } from '@/lib/api/types';
import type { HealthState, ProviderCapability, ProviderHealthResponse, ProviderResponse } from '@/lib/api/types';

/**
 * Every number the dashboard shows, derived in one place from the registry the API already
 * returned. Pure and synchronous on purpose: the dashboard makes exactly one request
 * (`GET /api/v1/providers`), and each card is a projection of that answer rather than a call of
 * its own. Counting client-side is not a shortcut here — the backend exposes no aggregate
 * endpoint, and eight requests to render eight numbers would be worse in every dimension.
 */

export interface ProviderSummary {
  total: number;
  enabled: number;
  disabled: number;
  /** Distinct `category` codes present in the registry — the verticals actually onboarded. */
  categories: string[];
  /** Providers with no base URL configured: registered, but pointing nowhere. */
  missingBaseUrl: number;
}

export function summariseProviders(providers: ProviderResponse[]): ProviderSummary {
  const enabled = providers.filter((provider) => provider.enabled).length;

  return {
    total: providers.length,
    enabled,
    disabled: providers.length - enabled,
    categories: [...new Set(providers.map((provider) => provider.category))].sort(),
    // A provider whose adapter has a hardcoded base URL is legitimate (the mock has none), so
    // this is reported as information, never as an error.
    missingBaseUrl: providers.filter((provider) => !provider.baseUrl).length,
  };
}

export interface CapabilityCount {
  capability: ProviderCapability;
  /** How many providers declare it. */
  total: number;
  /** How many *enabled* providers declare it — the number that reflects live platform ability. */
  enabled: number;
}

/**
 * Counts every capability the enum defines, including those no provider declares.
 *
 * <p>Iterating the enum rather than the data is the point: a capability that nothing supports is
 * the most interesting row on the screen, and building the list from what happens to be present
 * would silently omit it. `SEAT_RELEASE` and `TICKET_DOWNLOAD` are exactly this case today —
 * FlixBus declares neither, so they are supported only by the mock.
 */
export function countCapabilities(providers: ProviderResponse[]): CapabilityCount[] {
  return PROVIDER_CAPABILITIES.map((capability) => ({
    capability,
    total: providers.filter((provider) => provider.capabilities.includes(capability)).length,
    enabled: providers.filter(
      (provider) => provider.enabled && provider.capabilities.includes(capability),
    ).length,
  }));
}

export interface HealthSummary {
  /** Keyed by `HealthState`, plus `notProbed` for providers with no result this session. */
  counts: Record<HealthState, number>;
  notProbed: number;
  /** Providers currently failing, worst first — the actionable list. */
  failing: { code: string; displayName: string; state: HealthState; consecutiveFailures: number }[];
}

/**
 * Folds whatever probe results exist into a summary.
 *
 * <p>`probes` is deliberately a partial map. The health route is a live call to the provider, so
 * this console never probes on page load; most entries are absent most of the time, and a
 * provider with no result is reported as "not probed" rather than assumed healthy. Assuming
 * health from silence is how a dashboard ends up green during an outage.
 */
export function summariseHealth(
  providers: ProviderResponse[],
  probes: Map<string, ProviderHealthResponse>,
): HealthSummary {
  const counts: Record<HealthState, number> = {
    UNKNOWN: 0,
    HEALTHY: 0,
    DEGRADED: 0,
    UNAVAILABLE: 0,
  };

  let notProbed = 0;
  const failing: HealthSummary['failing'] = [];

  for (const provider of providers) {
    const probe = probes.get(provider.code);
    if (!probe) {
      notProbed += 1;
      continue;
    }

    counts[probe.currentState] += 1;

    if (probe.currentState === 'DEGRADED' || probe.currentState === 'UNAVAILABLE') {
      failing.push({
        code: provider.code,
        displayName: provider.displayName,
        state: probe.currentState,
        consecutiveFailures: probe.consecutiveFailures,
      });
    }
  }

  // Unavailable before degraded, then by how long it has been failing.
  failing.sort((a, b) => {
    if (a.state !== b.state) return a.state === 'UNAVAILABLE' ? -1 : 1;
    return b.consecutiveFailures - a.consecutiveFailures;
  });

  return { counts, notProbed, failing };
}
