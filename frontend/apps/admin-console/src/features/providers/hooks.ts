'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { providersApi } from './api';
import { queryKeys } from '@/lib/api/query-keys';
import type { ProviderRequest, ProviderResponse } from '@/lib/api/types';

export function useProviders(enabledOnly = false) {
  return useQuery({
    queryKey: queryKeys.providers.list(enabledOnly),
    queryFn: () => providersApi.list(enabledOnly),
    staleTime: 30_000,
  });
}

export function useProvider(id: string | null) {
  return useQuery({
    queryKey: queryKeys.providers.detail(id ?? ''),
    queryFn: () => providersApi.get(id!),
    enabled: Boolean(id),
  });
}

/**
 * Writes the server's own response into both caches it belongs to, rather than invalidating and
 * refetching. Every mutating route here returns the full updated `ProviderResponse`, so a refetch
 * would ask the server to repeat something it just told us.
 */
function useSyncProviderCaches() {
  const queryClient = useQueryClient();

  return (provider: ProviderResponse) => {
    queryClient.setQueryData(queryKeys.providers.detail(provider.id), provider);
    queryClient.setQueriesData<ProviderResponse[]>(
      { queryKey: queryKeys.providers.all },
      (existing) =>
        Array.isArray(existing)
          ? existing.map((candidate) => (candidate.id === provider.id ? provider : candidate))
          : existing,
    );
    // The enabled-only list is a different set, not a different rendering of the same one — a
    // provider that just changed state joins or leaves it.
    void queryClient.invalidateQueries({ queryKey: queryKeys.providers.list(true) });
  };
}

export function useRegisterProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ProviderRequest) => providersApi.register(body),
    onSuccess: (provider) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.providers.all });
      toast.success(`${provider.displayName} registered`, {
        description: 'It starts disabled. Store credentials and test the connection before enabling it.',
      });
    },
  });
}

export function useUpdateProvider() {
  const sync = useSyncProviderCaches();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ProviderRequest }) =>
      providersApi.update(id, body),
    onSuccess: (provider) => {
      sync(provider);
      toast.success(`${provider.displayName} updated`);
    },
  });
}

/**
 * Enable/disable, applied optimistically.
 *
 * <p>This is the one place optimism is clearly right: the toggle is the whole interaction, both
 * routes are idempotent, and the only field that changes is the boolean already on screen. A
 * failure rolls the row back and says so — the server's answer always wins.
 */
export function useSetProviderEnabled() {
  const queryClient = useQueryClient();
  const sync = useSyncProviderCaches();

  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean; displayName?: string }) =>
      enabled ? providersApi.enable(id) : providersApi.disable(id),

    onMutate: async ({ id, enabled }) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.providers.all });
      const previous = queryClient.getQueriesData<ProviderResponse[] | ProviderResponse>({
        queryKey: queryKeys.providers.all,
      });

      const applyOptimistic = <T,>(existing: T): T => {
        if (Array.isArray(existing)) {
          return existing.map((candidate: ProviderResponse) =>
            candidate.id === id ? { ...candidate, enabled } : candidate,
          ) as T;
        }
        const single = existing as ProviderResponse | undefined;
        return (single && single.id === id ? { ...single, enabled } : single) as T;
      };

      queryClient.setQueriesData({ queryKey: queryKeys.providers.all }, applyOptimistic);
      return { previous };
    },

    onError: (_error, variables, context) => {
      context?.previous.forEach(([key, value]) => queryClient.setQueryData(key, value));
      toast.error(
        variables.enabled ? 'Could not enable that provider' : 'Could not disable that provider',
      );
    },

    onSuccess: (provider) => {
      sync(provider);
      toast.success(
        provider.enabled
          ? `${provider.displayName} is now in service`
          : `${provider.displayName} is out of service`,
      );
    },
  });
}
