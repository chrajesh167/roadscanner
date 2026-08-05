'use client';

import * as React from 'react';
import Link from 'next/link';
import { ChevronLeft, Pencil, PlugZap } from 'lucide-react';
import { ProviderStatusBadge } from './provider-status-badge';
import { ProviderToggle } from './provider-toggle';
import { CapabilityList } from './capability-list';
import { ProviderForm } from './provider-form';
import { useProvider, useUpdateProvider } from '../hooks';
import { CredentialPanel } from '@/features/credentials/components/credential-panel';
import { ProviderHealthCard } from '@/features/health/components/provider-health-card';
import { useTestConnection } from '@/features/health/hooks';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { PageContainer } from '@/components/layout/app-shell';
import { formatDateTime, formatTimeout } from '@/lib/utils/format';
import type { ProviderResponse } from '@/lib/api/types';

export function ProviderDetailView({ providerId }: { providerId: string }) {
  const provider = useProvider(providerId);

  if (provider.isPending) return <DetailSkeleton />;

  if (provider.isError) {
    return (
      <PageContainer title="Provider">
        <ErrorState
          error={provider.error}
          title="Could not load this provider"
          onRetry={() => provider.refetch()}
        />
        <div className="mt-6 flex justify-center">
          <Button asChild variant="ghost" size="sm">
            <Link href="/providers">Back to providers</Link>
          </Button>
        </div>
      </PageContainer>
    );
  }

  return <Loaded provider={provider.data} />;
}

function Loaded({ provider }: { provider: ProviderResponse }) {
  const [editing, setEditing] = React.useState(false);
  const update = useUpdateProvider();
  const testConnection = useTestConnection();

  return (
    <PageContainer
      title={provider.displayName}
      description={
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-caption text-content-muted">{provider.code}</span>
          <span className="text-content-muted">·</span>
          <span>{provider.category}</span>
        </span>
      }
      actions={
        <>
          <Button
            variant="secondary"
            size="sm"
            loading={testConnection.isPending}
            loadingText="Probing…"
            onClick={() =>
              testConnection.mutate({
                id: provider.id,
                code: provider.code,
                displayName: provider.displayName,
              })
            }
          >
            <PlugZap />
            Test connection
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            <Pencil />
            Edit
          </Button>
        </>
      }
    >
      <Link
        href="/providers"
        className="mb-6 inline-flex w-fit items-center gap-1 text-caption text-content-secondary transition-colors hover:text-content"
      >
        <ChevronLeft className="size-4" />
        All providers
      </Link>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          <Card padding="md">
            <CardHeader className="mb-5">
              <CardTitle>Configuration</CardTitle>
              <CardDescription>
                What this provider is, and how the execution layer calls it.
              </CardDescription>
            </CardHeader>

            <dl className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2">
              <DetailRow label="Base URL">
                {provider.baseUrl ? (
                  <span className="break-all font-mono text-caption">{provider.baseUrl}</span>
                ) : (
                  <span className="text-content-muted">
                    None — the adapter supplies its own host
                  </span>
                )}
              </DetailRow>
              <DetailRow label="Registry id">
                <span className="break-all font-mono text-caption">{provider.id}</span>
              </DetailRow>
              <DetailRow label="Request timeout">{formatTimeout(provider.timeoutMs)}</DetailRow>
              <DetailRow label="Retries">
                {provider.retryCount} after the first failure
              </DetailRow>
              <DetailRow label="Registered">{formatDateTime(provider.createdAt)}</DetailRow>
              <DetailRow label="Last updated">{formatDateTime(provider.updatedAt)}</DetailRow>
            </dl>
          </Card>

          <Card padding="md">
            <CardHeader className="mb-5">
              <CardTitle>Capabilities</CardTitle>
              <CardDescription>
                Callers route work by this list. Struck-through entries are not declared, so this
                provider will refuse them rather than attempt them.
              </CardDescription>
            </CardHeader>
            <CapabilityList capabilities={provider.capabilities} showMissing />
          </Card>

          <CredentialPanel provider={provider} />
        </div>

        <div className="flex flex-col gap-6">
          <Card padding="md">
            <CardHeader className="mb-4">
              <CardTitle>Service state</CardTitle>
            </CardHeader>
            <div className="flex items-center justify-between gap-4">
              <ProviderStatusBadge enabled={provider.enabled} />
              <ProviderToggle provider={provider} />
            </div>
            <p className="mt-4 text-caption text-content-muted">
              {provider.enabled
                ? 'Search federates to this provider and seats can be held against it.'
                : 'Registered but not called. Test the connection before putting it into service.'}
            </p>
          </Card>

          <ProviderHealthCard provider={provider} />
        </div>
      </div>

      <Dialog
        open={editing}
        onOpenChange={(open) => {
          setEditing(open);
          if (!open) update.reset();
        }}
      >
        <DialogContent className="max-h-[90dvh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Edit {provider.displayName}</DialogTitle>
            <DialogDescription>
              A full replace of the editable fields. Use the service-state switch to change whether
              it is in service.
            </DialogDescription>
          </DialogHeader>

          <ProviderForm
            provider={provider}
            submitting={update.isPending}
            error={update.error}
            onCancel={() => setEditing(false)}
            onSubmit={(body) =>
              update.mutate(
                { id: provider.id, body },
                { onSuccess: () => setEditing(false) },
              )
            }
          />
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-micro uppercase tracking-[0.08em] text-content-muted">{label}</dt>
      <dd className="text-body text-content">{children}</dd>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <PageContainer title="Provider" description="Loading…">
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          <Card padding="md">
            <Skeleton className="mb-5 h-5 w-36" />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {Array.from({ length: 6 }).map((_, index) => (
                <div key={index} className="flex flex-col gap-2">
                  <Skeleton className="h-3 w-24" />
                  <Skeleton className="h-4 w-40" />
                </div>
              ))}
            </div>
          </Card>
          <Card padding="md">
            <Skeleton className="mb-5 h-5 w-32" />
            <Skeleton className="h-16 w-full" />
          </Card>
        </div>
        <div className="flex flex-col gap-6">
          <Card padding="md">
            <Skeleton className="h-20 w-full" />
          </Card>
          <Card padding="md">
            <Skeleton className="h-32 w-full" />
          </Card>
        </div>
      </div>
    </PageContainer>
  );
}
