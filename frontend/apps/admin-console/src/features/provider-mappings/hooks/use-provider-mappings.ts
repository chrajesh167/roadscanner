'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { locationsApi, providerMappingsApi } from '../api';
import { queryKeys } from '@/lib/api/query-keys';
import type {
  ProviderMapping,
  ProviderMappingPage,
  ProviderMappingQuery,
  ProviderMappingRequest,
} from '../types';

/**
 * The administrative listing.
 *
 * <p>`placeholderData: keepPreviousData` keeps the previous page on screen while the next one
 * loads, so paging and filtering do not flash a skeleton over a table the operator is reading.
 * The `isFetching` flag drives a subtler busy state instead.
 */
export function useProviderMappings(query: ProviderMappingQuery) {
  return useQuery({
    queryKey: queryKeys.providerMappings.list(query),
    queryFn: () => providerMappingsApi.list(query),
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  });
}

/**
 * Canonical locations with no mapping for a provider.
 *
 * <p>Disabled until a provider is chosen: the backend requires one, and firing the request without
 * it would trade a clear "choose a provider" prompt for a 400.
 */
export function useUnmappedLocations(provider: string | null, search: string) {
  return useQuery({
    queryKey: queryKeys.providerMappings.unmapped(provider, search),
    queryFn: () => providerMappingsApi.unmappedLocations(provider!, search),
    enabled: Boolean(provider),
    staleTime: 15_000,
  });
}

/**
 * Location autocomplete for the picker.
 *
 * <p>Read-only by construction — `locationsApi` exposes no write. A term shorter than two
 * characters is not sent: the backend does a prefix match, so one letter returns an arbitrary
 * slice of the catalogue that is no use to anyone choosing a specific place.
 */
export function useLocationSearch(term: string) {
  const trimmed = term.trim();

  return useQuery({
    queryKey: queryKeys.locations.search(trimmed),
    queryFn: () => locationsApi.search(trimmed),
    enabled: trimmed.length >= 2,
    staleTime: 60_000,
  });
}

/**
 * Invalidates every mapping list.
 *
 * <p>Blunt on purpose. A created, edited or deleted mapping changes which rows appear on which
 * page of which filter combination, and there is no way to work that out client-side without
 * reimplementing the backend's ordering. The lists are small and the alternative is a table that
 * disagrees with the database.
 */
function useInvalidateMappings() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.providerMappings.all });
  };
}

export function useCreateMapping() {
  const invalidate = useInvalidateMappings();

  return useMutation({
    mutationFn: (body: ProviderMappingRequest) => providerMappingsApi.create(body),
    onSuccess: (mapping) => {
      invalidate();
      toast.success(`${mapping.locationDisplayName} mapped to ${mapping.provider}`, {
        description: mapping.verified
          ? 'Recorded as confirmed.'
          : 'Recorded as unconfirmed — mark it verified once you have checked it against the provider.',
      });
    },
  });
}

export function useUpdateMapping() {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateMappings();

  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ProviderMappingRequest }) =>
      providerMappingsApi.update(id, body),
    onSuccess: (mapping) => {
      // The response is the updated row, so write it into every cached page that holds it before
      // invalidating — the table updates in place rather than blanking while the refetch runs.
      queryClient.setQueriesData<ProviderMappingPage>(
        { queryKey: queryKeys.providerMappings.all },
        (existing) =>
          existing && Array.isArray(existing.mappings)
            ? {
                ...existing,
                mappings: existing.mappings.map((row) => (row.id === mapping.id ? mapping : row)),
              }
            : existing,
      );
      invalidate();
      toast.success('Mapping updated');
    },
  });
}

/**
 * Delete, applied optimistically.
 *
 * <p>Optimism is right here for the same reason it is right for the provider toggle: the row
 * leaves the table and nothing else on screen depends on it, the route is idempotent, and a
 * failure can put back exactly what was removed. Every cached page is snapshotted before the row
 * is dropped, and the snapshot is restored wholesale on error — restoring only the row would lose
 * the totals, leaving a pager that disagrees with the rows beneath it.
 *
 * <p>The unmapped worklist is invalidated on success rather than patched: a deleted mapping means
 * its location has just *become* unmapped for that provider, which is a row appearing in a list
 * this mutation has no ordering information for.
 */
export function useDeleteMapping() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (mapping: ProviderMapping) => providerMappingsApi.remove(mapping.id),

    onMutate: async (mapping) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.providerMappings.all });

      const previous = queryClient.getQueriesData<ProviderMappingPage>({
        queryKey: queryKeys.providerMappings.all,
      });

      queryClient.setQueriesData<ProviderMappingPage>(
        { queryKey: queryKeys.providerMappings.all },
        (existing) => {
          if (!existing || !Array.isArray(existing.mappings)) return existing;
          const remaining = existing.mappings.filter((row) => row.id !== mapping.id);
          if (remaining.length === existing.mappings.length) return existing;

          return {
            ...existing,
            mappings: remaining,
            // Keep the count honest with what is now on screen; the refetch settles the exact
            // figure. A stale total is how a pager offers a page that no longer exists.
            totalElements: Math.max(0, existing.totalElements - 1),
          };
        },
      );

      return { previous };
    },

    onError: (_error, mapping, context) => {
      context?.previous.forEach(([key, value]) => queryClient.setQueryData(key, value));
      toast.error('Could not delete that mapping', {
        description: `${mapping.locationDisplayName} → ${mapping.provider} is unchanged.`,
      });
    },

    onSuccess: (_result, mapping) => {
      toast.success('Mapping deleted', {
        description: `${mapping.locationDisplayName} no longer resolves to ${mapping.provider}.`,
      });
    },

    // Runs on both paths: after a failure the rollback needs the server's version anyway, and
    // after a success the totals and page boundaries have moved.
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.providerMappings.all });
    },
  });
}
