'use client';

import * as React from 'react';
import Link from 'next/link';
import { ChevronRight, Plus, RefreshCw, Server } from 'lucide-react';
import { ProviderStatusBadge } from './provider-status-badge';
import { ProviderToggle } from './provider-toggle';
import { CapabilityList } from './capability-list';
import { ProviderForm } from './provider-form';
import { useProviders, useRegisterProvider } from '../hooks';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { Switch, TBody, TD, TH, THead, TR, Table } from '@/components/ui/misc';
import { PageContainer } from '@/components/layout/app-shell';
import { formatRelative, formatTimeout } from '@/lib/utils/format';

export function ProvidersView() {
  const [enabledOnly, setEnabledOnly] = React.useState(false);
  const [registering, setRegistering] = React.useState(false);

  const providers = useProviders(enabledOnly);
  const register = useRegisterProvider();

  return (
    <PageContainer
      title="Providers"
      description="The canonical registry. Every service resolves provider questions through it — none keeps its own copy."
      actions={
        <>
          <label className="flex items-center gap-2.5 text-caption text-content-secondary">
            <Switch
              checked={enabledOnly}
              onCheckedChange={setEnabledOnly}
              aria-label="Show only providers that are in service"
            />
            In service only
          </label>
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
          <Button size="sm" onClick={() => setRegistering(true)}>
            <Plus />
            Register
          </Button>
        </>
      }
    >
      {providers.isPending ? (
        <TableSkeleton />
      ) : providers.isError ? (
        <ErrorState
          error={providers.error}
          title="Could not load the registry"
          onRetry={() => providers.refetch()}
        />
      ) : providers.data.length === 0 ? (
        <EmptyState
          icon={<Server />}
          title={enabledOnly ? 'No providers are in service' : 'No providers registered'}
          description={
            enabledOnly
              ? 'Every registered provider is currently out of service. Turn off the filter to see them.'
              : 'Register a provider to begin. It starts disabled until you test its connection and put it into service.'
          }
          action={
            enabledOnly ? (
              <Button variant="secondary" onClick={() => setEnabledOnly(false)}>
                Show all providers
              </Button>
            ) : (
              <Button onClick={() => setRegistering(true)}>
                <Plus />
                Register a provider
              </Button>
            )
          }
        />
      ) : (
        <Card padding="none" className="overflow-hidden">
          <Table>
            <THead>
              <TR>
                <TH scope="col">Provider</TH>
                <TH scope="col" className="hidden md:table-cell">
                  Capabilities
                </TH>
                <TH scope="col" className="hidden lg:table-cell">
                  Execution
                </TH>
                <TH scope="col">Status</TH>
                <TH scope="col">
                  <span className="sr-only">In service</span>
                </TH>
                <TH scope="col">
                  <span className="sr-only">Open</span>
                </TH>
              </TR>
            </THead>
            <TBody>
              {providers.data.map((provider) => (
                <TR key={provider.id} className="transition-colors hover:bg-white/[0.02]">
                  <TD>
                    <Link
                      href={`/providers/${provider.id}`}
                      className="flex flex-col gap-1 rounded-sm text-content"
                    >
                      <span className="flex items-center gap-2.5">
                        <span className="font-medium">{provider.displayName}</span>
                        <span className="font-mono text-micro text-content-muted">
                          {provider.code}
                        </span>
                      </span>
                      <span className="text-caption text-content-muted">
                        {provider.category} · updated {formatRelative(provider.updatedAt)}
                      </span>
                    </Link>
                  </TD>

                  <TD className="hidden md:table-cell">
                    <CapabilityList capabilities={provider.capabilities} max={3} />
                  </TD>

                  <TD className="hidden whitespace-nowrap text-caption lg:table-cell">
                    {formatTimeout(provider.timeoutMs)} · {provider.retryCount} retr
                    {provider.retryCount === 1 ? 'y' : 'ies'}
                  </TD>

                  <TD>
                    <ProviderStatusBadge enabled={provider.enabled} />
                  </TD>

                  <TD>
                    <ProviderToggle provider={provider} />
                  </TD>

                  <TD>
                    <Link
                      href={`/providers/${provider.id}`}
                      aria-label={`Open ${provider.displayName}`}
                      className="grid size-8 place-items-center rounded-sm text-content-muted transition-colors hover:bg-white/[0.07] hover:text-content"
                    >
                      <ChevronRight className="size-4" />
                    </Link>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </Card>
      )}

      <Dialog
        open={registering}
        onOpenChange={(open) => {
          setRegistering(open);
          if (!open) register.reset();
        }}
      >
        <DialogContent className="max-h-[90dvh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Register a provider</DialogTitle>
            <DialogDescription>
              Adds a registry entry. It starts out of service — an adapter implementing
              `ProviderClient` must also exist for the code you enter, or nothing will be able to
              call it.
            </DialogDescription>
          </DialogHeader>

          <ProviderForm
            submitting={register.isPending}
            error={register.error}
            onCancel={() => setRegistering(false)}
            onSubmit={(body) =>
              register.mutate(body, {
                onSuccess: () => setRegistering(false),
              })
            }
          />
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}

function TableSkeleton() {
  return (
    <Card padding="none" className="overflow-hidden">
      <div className="flex flex-col divide-y divide-line">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className="flex items-center gap-4 px-4 py-4">
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-4 w-44" />
              <Skeleton className="h-3 w-28" />
            </div>
            <Skeleton className="hidden h-6 w-52 md:block" />
            <Skeleton className="h-6 w-24" />
            <Skeleton className="h-6 w-11 rounded-full" />
          </div>
        ))}
      </div>
    </Card>
  );
}
