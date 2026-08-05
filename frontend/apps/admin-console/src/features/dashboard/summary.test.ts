import { describe, expect, it } from 'vitest';
import { countCapabilities, summariseHealth, summariseProviders } from './summary';
import type { ProviderHealthResponse, ProviderResponse } from '@/lib/api/types';

/**
 * The dashboard's numbers are the one place this app computes rather than displays, so they are
 * the one place a unit test earns its keep. The cases below are drawn from the registry the
 * platform actually seeds: an enabled mock with every capability, and a disabled FlixBus that
 * deliberately declares neither SEAT_RELEASE nor TICKET_DOWNLOAD.
 */

function provider(overrides: Partial<ProviderResponse> = {}): ProviderResponse {
  return {
    id: 'a1',
    code: 'MOCK',
    category: 'BUS',
    displayName: 'Mock Provider',
    enabled: true,
    capabilities: ['SEARCH', 'SEAT_MAP'],
    baseUrl: null,
    timeoutMs: 5_000,
    retryCount: 2,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

function health(overrides: Partial<ProviderHealthResponse> = {}): ProviderHealthResponse {
  return {
    providerType: 'MOCK',
    currentState: 'HEALTHY',
    lastCheckedAt: '2026-08-04T12:00:00Z',
    lastSuccessAt: '2026-08-04T12:00:00Z',
    lastFailureAt: null,
    consecutiveFailures: 0,
    ...overrides,
  };
}

describe('summariseProviders', () => {
  it('splits the registry into in-service and out-of-service', () => {
    const summary = summariseProviders([
      provider({ id: 'a', enabled: true }),
      provider({ id: 'b', code: 'FLIXBUS', enabled: false }),
      provider({ id: 'c', code: 'OTHER', enabled: false }),
    ]);

    expect(summary.total).toBe(3);
    expect(summary.enabled).toBe(1);
    expect(summary.disabled).toBe(2);
  });

  it('reports each vertical once, sorted', () => {
    const summary = summariseProviders([
      provider({ id: 'a', category: 'RAIL' }),
      provider({ id: 'b', category: 'BUS' }),
      provider({ id: 'c', category: 'BUS' }),
    ]);

    expect(summary.categories).toEqual(['BUS', 'RAIL']);
  });

  it('counts providers with no base URL, which is legitimate rather than broken', () => {
    const summary = summariseProviders([
      provider({ id: 'a', baseUrl: null }),
      provider({ id: 'b', baseUrl: 'https://global.api.flixbus.com' }),
    ]);

    expect(summary.missingBaseUrl).toBe(1);
  });

  it('handles an empty registry without dividing by anything', () => {
    const summary = summariseProviders([]);

    expect(summary).toEqual({
      total: 0,
      enabled: 0,
      disabled: 0,
      categories: [],
      missingBaseUrl: 0,
    });
  });
});

describe('countCapabilities', () => {
  it('reports every capability the enum defines, including unsupported ones', () => {
    const counts = countCapabilities([provider({ capabilities: ['SEARCH'] })]);

    // Nine capabilities exist; a screen built only from present data would show one.
    expect(counts).toHaveLength(9);
    expect(counts.find((row) => row.capability === 'TICKET_DOWNLOAD')).toEqual({
      capability: 'TICKET_DOWNLOAD',
      total: 0,
      enabled: 0,
    });
  });

  it('separates declared from actually available', () => {
    const counts = countCapabilities([
      provider({ id: 'a', code: 'MOCK', enabled: true, capabilities: ['SEARCH', 'SEAT_RELEASE'] }),
      provider({ id: 'b', code: 'FLIXBUS', enabled: false, capabilities: ['SEARCH'] }),
    ]);

    const search = counts.find((row) => row.capability === 'SEARCH');
    expect(search).toEqual({ capability: 'SEARCH', total: 2, enabled: 1 });
  });

  it('shows a capability declared only by disabled providers as unavailable', () => {
    const counts = countCapabilities([
      provider({ enabled: false, capabilities: ['BOOKING_CONFIRMATION'] }),
    ]);

    const confirmation = counts.find((row) => row.capability === 'BOOKING_CONFIRMATION');
    expect(confirmation?.total).toBe(1);
    expect(confirmation?.enabled).toBe(0);
  });
});

describe('summariseHealth', () => {
  it('counts a provider with no probe as not probed rather than healthy', () => {
    const summary = summariseHealth([provider()], new Map());

    expect(summary.notProbed).toBe(1);
    expect(summary.counts.HEALTHY).toBe(0);
    expect(summary.failing).toEqual([]);
  });

  it('folds probe results into per-state counts', () => {
    const summary = summariseHealth(
      [
        provider({ id: 'a', code: 'MOCK' }),
        provider({ id: 'b', code: 'FLIXBUS' }),
        provider({ id: 'c', code: 'NEVER_PROBED' }),
      ],
      new Map([
        ['MOCK', health({ providerType: 'MOCK', currentState: 'HEALTHY' })],
        ['FLIXBUS', health({ providerType: 'FLIXBUS', currentState: 'DEGRADED' })],
      ]),
    );

    expect(summary.counts.HEALTHY).toBe(1);
    expect(summary.counts.DEGRADED).toBe(1);
    expect(summary.notProbed).toBe(1);
  });

  it('ranks unavailable above degraded, then by how long each has been failing', () => {
    const summary = summariseHealth(
      [
        provider({ id: 'a', code: 'A', displayName: 'A' }),
        provider({ id: 'b', code: 'B', displayName: 'B' }),
        provider({ id: 'c', code: 'C', displayName: 'C' }),
      ],
      new Map([
        ['A', health({ providerType: 'A', currentState: 'DEGRADED', consecutiveFailures: 9 })],
        ['B', health({ providerType: 'B', currentState: 'UNAVAILABLE', consecutiveFailures: 2 })],
        ['C', health({ providerType: 'C', currentState: 'DEGRADED', consecutiveFailures: 12 })],
      ]),
    );

    expect(summary.failing.map((row) => row.code)).toEqual(['B', 'C', 'A']);
  });

  it('does not treat UNKNOWN as a failure worth listing', () => {
    const summary = summariseHealth(
      [provider({ code: 'MOCK' })],
      new Map([['MOCK', health({ currentState: 'UNKNOWN' })]]),
    );

    expect(summary.counts.UNKNOWN).toBe(1);
    expect(summary.failing).toEqual([]);
  });
});
