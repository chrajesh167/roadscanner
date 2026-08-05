'use client';

import * as React from 'react';
import Link from 'next/link';
import { KeyRound, Server, ShieldCheck } from 'lucide-react';
import { CredentialPanel } from './credential-panel';
import { useCredentials } from '../hooks';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { PageContainer } from '@/components/layout/app-shell';
import { useProviders } from '@/features/providers/hooks';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';
import type { ProviderResponse } from '@/lib/api/types';

/**
 * Credential state for the whole registry: pick a provider on the left, manage its secrets on the
 * right.
 *
 * <p>A master/detail layout rather than a table of forms, because a credential write is a full
 * replacement of one provider's secret set — putting several such forms on one screen invites
 * exactly the mistake of typing into the wrong row.
 */
export function CredentialsView() {
  const providers = useProviders();
  const [selectedId, setSelectedId] = React.useState<string | null>(null);

  const selected = providers.data?.find((provider) => provider.id === selectedId) ?? null;

  // Select the first provider once the registry lands, so the screen is never a dead end.
  React.useEffect(() => {
    if (!selectedId && providers.data && providers.data.length > 0) {
      setSelectedId(providers.data[0]!.id);
    }
  }, [providers.data, selectedId]);

  return (
    <PageContainer
      title="Credentials"
      description="Partner secrets are write-only. The platform reports whether they exist and when they changed — never their values."
    >
      {providers.isPending ? (
        <CredentialsSkeleton />
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
          description="Credentials belong to a provider. Register one first."
          action={
            <Button asChild>
              <Link href="/providers">Go to Providers</Link>
            </Button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <Card padding="none" className="h-fit overflow-hidden lg:col-span-1">
            <ul className="flex flex-col divide-y divide-line" role="list">
              {providers.data.map((provider) => (
                <li key={provider.id}>
                  <ProviderPickerRow
                    provider={provider}
                    selected={provider.id === selectedId}
                    onSelect={() => setSelectedId(provider.id)}
                  />
                </li>
              ))}
            </ul>
          </Card>

          <div className="lg:col-span-2">
            {selected ? (
              <CredentialPanel provider={selected} />
            ) : (
              <Card padding="lg">
                <EmptyState
                  icon={<KeyRound />}
                  title="Select a provider"
                  description="Choose a provider to see its credential state."
                />
              </Card>
            )}
          </div>
        </div>
      )}
    </PageContainer>
  );
}

/**
 * A row in the picker, showing at a glance whether this provider has anything stored. The
 * presence query is cheap and cached; it returns flags and a timestamp, never a secret.
 */
function ProviderPickerRow({
  provider,
  selected,
  onSelect,
}: {
  provider: ProviderResponse;
  selected: boolean;
  onSelect: () => void;
}) {
  const credentials = useCredentials(provider.id);

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={selected ? 'true' : undefined}
      className={cn(
        'flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left transition-colors',
        selected ? 'bg-accent-soft' : 'hover:bg-white/[0.03]',
      )}
    >
      <span className="flex min-w-0 flex-col gap-1">
        <span className="flex items-center gap-2">
          <span className="truncate text-body text-content">{provider.displayName}</span>
          <span className="shrink-0 font-mono text-micro text-content-muted">{provider.code}</span>
        </span>
        <span className="text-caption text-content-muted">
          {credentials.isPending
            ? 'Checking…'
            : credentials.data
              ? `Changed ${formatRelative(credentials.data.updatedAt)}`
              : 'No credentials stored'}
        </span>
      </span>

      {credentials.data && (
        <Badge tone={credentials.data.encrypted ? 'success' : 'warning'} className="shrink-0">
          <ShieldCheck />
          {credentials.data.encrypted ? 'Encrypted' : 'Legacy'}
        </Badge>
      )}
    </button>
  );
}

function CredentialsSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <Card padding="none" className="h-fit overflow-hidden">
        <div className="flex flex-col divide-y divide-line">
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="flex flex-col gap-2 px-4 py-4">
              <Skeleton className="h-4 w-36" />
              <Skeleton className="h-3 w-28" />
            </div>
          ))}
        </div>
      </Card>
      <Card padding="md" className="lg:col-span-2">
        <Skeleton className="mb-5 h-5 w-32" />
        <Skeleton className="mb-3 h-20 w-full" />
        <Skeleton className="h-40 w-full" />
      </Card>
    </div>
  );
}
