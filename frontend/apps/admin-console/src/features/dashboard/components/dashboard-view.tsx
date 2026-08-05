'use client';

import Link from 'next/link';
import { useQueries, useQueryClient } from '@tanstack/react-query';
import { CircleSlash, Layers, PowerOff, RefreshCw, Server, Zap } from 'lucide-react';
import { StatCard, StatCardSkeleton } from './stat-card';
import { CapabilityBreakdown } from './capability-breakdown';
import { HealthSummaryCard } from './health-summary-card';
import { countCapabilities, summariseHealth, summariseProviders } from '../summary';
import { ProviderStatusBadge } from '@/features/providers/components/provider-status-badge';
import { useProviders } from '@/features/providers/hooks';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { PageContainer } from '@/components/layout/app-shell';
import { queryKeys } from '@/lib/api/query-keys';
import { formatRelative } from '@/lib/utils/format';
import type { ProviderHealthResponse } from '@/lib/api/types';

/**
 * The dashboard makes exactly one request — `GET /api/v1/providers` — and derives every card from
 * it. Health is read out of the query cache rather than fetched, because probing is a live call
 * to a partner's API that only an explicit action should trigger.
 */
export function DashboardView() {
  const providers = useProviders();
  const queryClient = useQueryClient();

  // Cache *subscriptions*, not fetches: each query is disabled, so nothing here reaches the
  // network, but a probe run elsewhere in the session updates this screen instead of leaving it
  // showing a snapshot from whenever it happened to mount.
  const probeQueries = useQueries({
    queries: (providers.data ?? []).map((provider) => ({
      queryKey: queryKeys.health.probe(provider.code),
      enabled: false,
      initialData: () =>
        queryClient.getQueryData<ProviderHealthResponse>(queryKeys.health.probe(provider.code)),
    })),
  });

  const probes = new Map<string, ProviderHealthResponse>();
  for (const query of probeQueries) {
    const health = query.data as ProviderHealthResponse | undefined;
    if (health) probes.set(health.providerType, health);
  }

  if (providers.isPending) return <DashboardSkeleton />;

  if (providers.isError) {
    return (
      <PageContainer title="Dashboard">
        <ErrorState
          error={providers.error}
          title="Could not load the provider registry"
          onRetry={() => providers.refetch()}
        />
      </PageContainer>
    );
  }

  const summary = summariseProviders(providers.data);
  const capabilities = countCapabilities(providers.data);
  const health = summariseHealth(providers.data, probes);

  return (
    <PageContainer
      title="Dashboard"
      description="The state of the provider registry: what is registered, what is in service, and what it can do."
      actions={
        <Button
          variant="secondary"
          size="sm"
          onClick={() => providers.refetch()}
          loading={providers.isFetching}
          loadingText="Refreshing…"
        >
          <RefreshCw />
          Refresh
        </Button>
      }
    >
      {summary.total === 0 ? (
        <EmptyState
          icon={<Server />}
          title="No providers registered"
          description="The registry is empty. Register a provider to begin — it will start disabled until you test its connection."
          action={
            <Button asChild>
              <Link href="/providers">Go to Providers</Link>
            </Button>
          }
        />
      ) : (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="Registered"
              value={summary.total}
              hint={`${summary.categories.join(', ') || 'No category'}`}
              icon={Server}
              tone="accent"
            />
            <StatCard
              label="In service"
              value={summary.enabled}
              hint="Reachable by search and booking"
              icon={Zap}
              tone={summary.enabled > 0 ? 'success' : 'warning'}
            />
            <StatCard
              label="Out of service"
              value={summary.disabled}
              hint="Registered, not currently used"
              icon={PowerOff}
            />
            <StatCard
              label="No base URL"
              value={summary.missingBaseUrl}
              hint="Adapter-supplied or unconfigured"
              icon={CircleSlash}
            />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <CapabilityBreakdown counts={capabilities} providerCount={summary.total} />
            <HealthSummaryCard summary={health} />
          </div>

          <Card padding="md">
            <CardHeader className="mb-5">
              <CardTitle>Registry</CardTitle>
              <CardDescription>Every provider the platform knows about.</CardDescription>
            </CardHeader>

            <ul className="flex flex-col divide-y divide-line">
              {providers.data.map((provider) => (
                <li key={provider.id}>
                  <Link
                    href={`/providers/${provider.id}`}
                    className="flex items-center justify-between gap-4 py-3 transition-colors hover:text-content"
                  >
                    <span className="flex min-w-0 flex-col gap-1">
                      <span className="flex items-center gap-2.5">
                        <span className="truncate text-body text-content">{provider.displayName}</span>
                        <span className="shrink-0 font-mono text-micro text-content-muted">
                          {provider.code}
                        </span>
                      </span>
                      <span className="text-caption text-content-muted">
                        <Layers className="mr-1 inline size-3" aria-hidden />
                        {provider.capabilities.length} capabilities · updated{' '}
                        {formatRelative(provider.updatedAt)}
                      </span>
                    </span>
                    <ProviderStatusBadge enabled={provider.enabled} />
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      )}
    </PageContainer>
  );
}

function DashboardSkeleton() {
  return (
    <PageContainer title="Dashboard" description="Loading the provider registry…">
      <div className="flex flex-col gap-6">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <StatCardSkeleton key={index} />
          ))}
        </div>
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Card padding="md">
            <Skeleton className="mb-5 h-5 w-40" />
            <div className="flex flex-col gap-3">
              {Array.from({ length: 9 }).map((_, index) => (
                <Skeleton key={index} className="h-4 w-full" />
              ))}
            </div>
          </Card>
          <Card padding="md">
            <Skeleton className="mb-5 h-5 w-36" />
            <Skeleton className="h-24 w-full" />
          </Card>
        </div>
      </div>
    </PageContainer>
  );
}
