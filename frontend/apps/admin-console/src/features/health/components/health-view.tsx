'use client';

import * as React from 'react';
import Link from 'next/link';
import { useQueries, useQueryClient } from '@tanstack/react-query';
import { Activity, Info, Server } from 'lucide-react';
import { Badge, healthTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { TBody, TD, TH, THead, TR, Table, Tooltip } from '@/components/ui/misc';
import { PageContainer } from '@/components/layout/app-shell';
import { ProviderStatusBadge } from '@/features/providers/components/provider-status-badge';
import { useProviders } from '@/features/providers/hooks';
import { queryKeys } from '@/lib/api/query-keys';
import { formatDateTime, formatRelative, humanizeEnum } from '@/lib/utils/format';
import { useProbeHealth } from '../hooks';
import type { ProviderHealthResponse, ProviderResponse } from '@/lib/api/types';

/**
 * Health across the registry.
 *
 * <p>Nothing on this screen fetches health automatically, and the copy says so. The route behind
 * "Probe" is `CheckProviderHealth`, which calls the provider, records the outcome and returns it —
 * a page that probed on mount would turn opening a browser tab into live traffic against every
 * partner the platform integrates with, and a page that polled would do it continuously.
 *
 * <p>The durable health record the scheduled monitor maintains has no read-only endpoint today.
 * When it gets one, this screen should load that on mount and keep "Probe" for the explicit
 * check — see `features/health/api.ts`.
 */
export function HealthView() {
  const providers = useProviders();
  const probe = useProbeHealth();
  const queryClient = useQueryClient();

  const probeable = React.useMemo(
    () => (providers.data ?? []).filter((provider) => provider.capabilities.includes('HEALTH_CHECK')),
    [providers.data],
  );

  // Cache subscriptions, not fetches: every one is disabled, so this only re-renders the table
  // when a probe writes a result in.
  const cached = useQueries({
    queries: (providers.data ?? []).map((provider) => ({
      queryKey: queryKeys.health.probe(provider.code),
      enabled: false,
      initialData: () =>
        queryClient.getQueryData<ProviderHealthResponse>(queryKeys.health.probe(provider.code)),
    })),
  });

  const healthByCode = new Map<string, ProviderHealthResponse>();
  (providers.data ?? []).forEach((provider, index) => {
    const result = cached[index]?.data as ProviderHealthResponse | undefined;
    if (result) healthByCode.set(provider.code, result);
  });

  const probeAll = () => {
    for (const provider of probeable) {
      probe.mutate({ code: provider.code, displayName: provider.displayName });
    }
  };

  return (
    <PageContainer
      title="Health"
      description="On-demand probes against each provider. Results are held for this session only."
      actions={
        probeable.length > 0 && (
          <Button
            size="sm"
            onClick={probeAll}
            loading={probe.isPending}
            loadingText="Probing…"
          >
            <Activity />
            Probe all ({probeable.length})
          </Button>
        )
      }
    >
      <Card
        padding="md"
        className="mb-6 flex items-start gap-3 border-info/25 bg-info-soft"
      >
        <Info className="mt-0.5 size-4 shrink-0 text-info" aria-hidden />
        <p className="text-caption text-content-secondary">
          Probing is a live call to the provider&apos;s API, so nothing here runs automatically or
          on a timer. The scheduled health monitor inside provider-integration-service keeps its own
          durable record; it has no read-only endpoint yet, so this screen shows only what you
          probe.
        </p>
      </Card>

      {providers.isPending ? (
        <HealthSkeleton />
      ) : providers.isError ? (
        <ErrorState
          error={providers.error}
          title="Could not load the provider registry"
          onRetry={() => providers.refetch()}
        />
      ) : providers.data.length === 0 ? (
        <EmptyState
          icon={<Server />}
          title="No providers registered"
          description="There is nothing to probe until a provider exists in the registry."
          action={
            <Button asChild>
              <Link href="/providers">Go to Providers</Link>
            </Button>
          }
        />
      ) : (
        <Card padding="none" className="overflow-hidden">
          <Table>
            <THead>
              <TR>
                <TH scope="col">Provider</TH>
                <TH scope="col">State</TH>
                <TH scope="col" className="hidden sm:table-cell">
                  Last checked
                </TH>
                <TH scope="col" className="hidden lg:table-cell">
                  Last success
                </TH>
                <TH scope="col" className="hidden md:table-cell">
                  Failures
                </TH>
                <TH scope="col">
                  <span className="sr-only">Probe</span>
                </TH>
              </TR>
            </THead>
            <TBody>
              {providers.data.map((provider) => (
                <HealthRow
                  key={provider.id}
                  provider={provider}
                  health={healthByCode.get(provider.code)}
                />
              ))}
            </TBody>
          </Table>
        </Card>
      )}
    </PageContainer>
  );
}

function HealthRow({
  provider,
  health,
}: {
  provider: ProviderResponse;
  health: ProviderHealthResponse | undefined;
}) {
  const probe = useProbeHealth();
  const canProbe = provider.capabilities.includes('HEALTH_CHECK');

  return (
    <TR className="transition-colors hover:bg-white/[0.02]">
      <TD>
        <Link href={`/providers/${provider.id}`} className="flex flex-col gap-1 text-content">
          <span className="flex items-center gap-2.5">
            <span className="font-medium">{provider.displayName}</span>
            <span className="font-mono text-micro text-content-muted">{provider.code}</span>
          </span>
          <ProviderStatusBadge enabled={provider.enabled} />
        </Link>
      </TD>

      <TD>
        {health ? (
          <Badge tone={healthTone(health.currentState)}>{humanizeEnum(health.currentState)}</Badge>
        ) : (
          <span className="text-caption text-content-muted">Not probed</span>
        )}
      </TD>

      <TD className="hidden whitespace-nowrap text-caption sm:table-cell">
        {health ? (
          <Tooltip content={formatDateTime(health.lastCheckedAt)}>
            <span>{formatRelative(health.lastCheckedAt)}</span>
          </Tooltip>
        ) : (
          <span className="text-content-muted">—</span>
        )}
      </TD>

      <TD className="hidden whitespace-nowrap text-caption lg:table-cell">
        {health?.lastSuccessAt ? (
          formatRelative(health.lastSuccessAt)
        ) : health ? (
          <span className="text-content-muted">Never</span>
        ) : (
          <span className="text-content-muted">—</span>
        )}
      </TD>

      <TD className="hidden text-caption tabular-nums md:table-cell">
        {health ? health.consecutiveFailures : <span className="text-content-muted">—</span>}
      </TD>

      <TD>
        {canProbe ? (
          <Button
            variant="ghost"
            size="sm"
            loading={probe.isPending && probe.variables?.code === provider.code}
            onClick={() => probe.mutate({ code: provider.code, displayName: provider.displayName })}
          >
            <Activity />
            Probe
          </Button>
        ) : (
          <Tooltip content="This provider does not declare HEALTH_CHECK">
            <span className="text-caption text-content-muted">Unsupported</span>
          </Tooltip>
        )}
      </TD>
    </TR>
  );
}

function HealthSkeleton() {
  return (
    <Card padding="none" className="overflow-hidden">
      <div className="flex flex-col divide-y divide-line">
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={index} className="flex items-center gap-4 px-4 py-4">
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-5 w-24 rounded-full" />
            </div>
            <Skeleton className="h-6 w-20 rounded-full" />
            <Skeleton className="hidden h-4 w-24 sm:block" />
            <Skeleton className="h-8 w-20" />
          </div>
        ))}
      </div>
    </Card>
  );
}
