'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { healthApi } from './api';
import { providersApi } from '@/features/providers/api';
import { queryKeys } from '@/lib/api/query-keys';
import type { ProviderHealthResponse } from '@/lib/api/types';

/**
 * Probing is modelled as a mutation, not a query, because it is one: the call reaches a partner's
 * API and writes a durable health record. Queries are expected to be safe to refetch on mount, on
 * focus and on reconnect — none of which is true here.
 *
 * <p>Results land in the query cache by hand so the screen can render the last known state for
 * each provider without ever fetching it automatically.
 */
export function useProbeHealth() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (provider: { code: string; displayName: string }) => healthApi.probe(provider.code),
    onSuccess: (health, provider) => {
      queryClient.setQueryData(queryKeys.health.probe(provider.code), health);
      if (health.currentState === 'HEALTHY') {
        toast.success(`${provider.displayName} is healthy`);
      } else {
        toast.warning(`${provider.displayName} reports ${health.currentState.toLowerCase()}`, {
          description:
            health.consecutiveFailures > 0
              ? `${health.consecutiveFailures} consecutive failure${health.consecutiveFailures === 1 ? '' : 's'}.`
              : undefined,
        });
      }
    },
    onError: (_error, provider) => {
      toast.error(`Could not probe ${provider.displayName}`, {
        description: 'The provider could not be reached, or it has no health-check capability.',
      });
    },
  });
}

/**
 * The admin-API equivalent, addressed by registry id. Same probe, same response shape — it exists
 * separately because the provider detail screen holds an id and this route is the documented one
 * for "test this connection" from the registry.
 */
export function useTestConnection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (provider: { id: string; code: string; displayName: string }) =>
      providersApi.test(provider.id),
    onSuccess: (health: ProviderHealthResponse, provider) => {
      queryClient.setQueryData(queryKeys.health.probe(provider.code), health);
      if (health.currentState === 'HEALTHY') {
        toast.success(`${provider.displayName} answered`, {
          description: 'The connection works with the stored configuration.',
        });
      } else {
        toast.warning(`${provider.displayName} reports ${health.currentState.toLowerCase()}`);
      }
    },
    onError: (_error, provider) => {
      toast.error(`${provider.displayName} could not be reached`, {
        description: 'Check the base URL and stored credentials, then try again.',
      });
    },
  });
}

export function useRefreshSession() {
  return useMutation({
    mutationFn: (provider: { id: string; displayName: string }) =>
      providersApi.refreshSession(provider.id),
    onSuccess: (session, provider) => {
      toast.success(`New session for ${provider.displayName}`, {
        description: `Valid until ${new Date(session.expiresAt).toLocaleString()}.`,
      });
    },
    onError: (_error, provider) => {
      toast.error(`${provider.displayName} rejected the stored credentials`, {
        description: 'Replace them under Credentials, then refresh the session again.',
      });
    },
  });
}
