'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, CircleDashed } from 'lucide-react';
import { Badge, healthTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { queryKeys } from '@/lib/api/query-keys';
import { formatDateTime, formatRelative, humanizeEnum } from '@/lib/utils/format';
import { useProbeHealth } from '../hooks';
import type { ProviderHealthResponse, ProviderResponse } from '@/lib/api/types';

/**
 * Health for one provider, read from the cache and only ever filled by an explicit probe.
 *
 * <p>The query below has no `queryFn` that reaches the network — `enabled: false` makes it a pure
 * cache subscription. That is the whole point: the health route performs a live call to the
 * provider, so a component that fetched on mount would make opening a screen generate traffic
 * against a partner's API.
 */
export function ProviderHealthCard({ provider }: { provider: ProviderResponse }) {
  const queryClient = useQueryClient();
  const probe = useProbeHealth();

  const { data: health } = useQuery<ProviderHealthResponse>({
    queryKey: queryKeys.health.probe(provider.code),
    enabled: false,
    initialData: () =>
      queryClient.getQueryData<ProviderHealthResponse>(queryKeys.health.probe(provider.code)),
  });

  const canProbe = provider.capabilities.includes('HEALTH_CHECK');

  return (
    <Card padding="md">
      <CardHeader className="mb-4">
        <CardTitle>Health</CardTitle>
        <CardDescription>Probed on demand, never polled.</CardDescription>
      </CardHeader>

      {health ? (
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-3">
            <Badge tone={healthTone(health.currentState)} size="md">
              {humanizeEnum(health.currentState)}
            </Badge>
            {health.consecutiveFailures > 0 && (
              <span className="text-caption tabular-nums text-content-secondary">
                {health.consecutiveFailures} consecutive failure
                {health.consecutiveFailures === 1 ? '' : 's'}
              </span>
            )}
          </div>

          <dl className="flex flex-col gap-2.5 border-t border-line pt-4">
            <HealthRow label="Last checked" value={formatDateTime(health.lastCheckedAt)} />
            <HealthRow
              label="Last success"
              value={health.lastSuccessAt ? formatRelative(health.lastSuccessAt) : 'Never'}
            />
            <HealthRow
              label="Last failure"
              value={health.lastFailureAt ? formatRelative(health.lastFailureAt) : 'Never'}
            />
          </dl>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3 py-4 text-center">
          <span
            className="grid size-10 place-items-center rounded-full border border-line-strong bg-white/[0.04] text-content-muted [&_svg]:size-4"
            aria-hidden
          >
            <CircleDashed />
          </span>
          <p className="text-caption text-content-secondary">
            Not probed in this session.
          </p>
        </div>
      )}

      <Button
        variant="secondary"
        size="sm"
        full
        className="mt-4"
        disabled={!canProbe}
        loading={probe.isPending}
        loadingText="Probing…"
        onClick={() => probe.mutate({ code: provider.code, displayName: provider.displayName })}
      >
        <Activity />
        {health ? 'Probe again' : 'Probe now'}
      </Button>

      {!canProbe && (
        <p className="mt-2.5 text-caption text-content-muted">
          This provider does not declare HEALTH_CHECK, so there is nothing to probe.
        </p>
      )}
    </Card>
  );
}

function HealthRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-caption text-content-muted">{label}</dt>
      <dd className="text-caption text-content">{value}</dd>
    </div>
  );
}
