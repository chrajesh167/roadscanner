'use client';

import { RefreshCw } from 'lucide-react';
import { CredentialForm } from './credential-form';
import { CredentialSummary } from './credential-summary';
import { useCredentials } from '../hooks';
import { useRefreshSession } from '@/features/health/hooks';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import type { ProviderResponse } from '@/lib/api/types';

/**
 * Credential state and rotation for one provider, used by both the Credentials screen and the
 * provider detail screen.
 *
 * <p>Session refresh sits here rather than under Health because it is the natural last step of a
 * rotation: it authenticates with whatever is stored right now, so it is how an admin proves new
 * secrets work before putting the provider into service.
 */
export function CredentialPanel({ provider }: { provider: ProviderResponse }) {
  const credentials = useCredentials(provider.id);
  const refreshSession = useRefreshSession();

  return (
    <Card padding="md">
      <CardHeader className="mb-5">
        <CardTitle>Credentials</CardTitle>
        <CardDescription>
          Write-only. The platform reports whether secrets exist and when they changed — never
          their values, from any endpoint.
        </CardDescription>
      </CardHeader>

      {credentials.isPending ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-8 w-56" />
        </div>
      ) : credentials.isError ? (
        <ErrorState
          error={credentials.error}
          title="Could not read credential state"
          onRetry={() => credentials.refetch()}
        />
      ) : (
        <div className="flex flex-col gap-6">
          <CredentialSummary summary={credentials.data} />

          {credentials.data && (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-elevated px-4 py-3">
              <p className="text-caption text-content-secondary">
                Prove the stored secrets authenticate against {provider.displayName}.
              </p>
              <Button
                variant="secondary"
                size="sm"
                loading={refreshSession.isPending}
                loadingText="Authenticating…"
                onClick={() =>
                  refreshSession.mutate({ id: provider.id, displayName: provider.displayName })
                }
              >
                <RefreshCw />
                Refresh session
              </Button>
            </div>
          )}

          <div className="border-t border-line pt-6">
            <h3 className="mb-1 text-body font-medium text-content">
              {credentials.data ? 'Replace credentials' : 'Store credentials'}
            </h3>
            <p className="mb-5 text-caption text-content-muted">
              A provider may authenticate by email and password, by a pre-issued token, or by both.
            </p>
            <CredentialForm
              providerId={provider.id}
              providerName={provider.displayName}
              hasExisting={credentials.data !== null}
            />
          </div>
        </div>
      )}
    </Card>
  );
}
