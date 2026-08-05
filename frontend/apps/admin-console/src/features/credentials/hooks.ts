'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { credentialsApi } from './api';
import { ApiError } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/query-keys';
import type { ProviderCredentialsRequest, ProviderCredentialsResponse } from '@/lib/api/types';

/**
 * Credential state for one provider.
 *
 * <p>A 404 is resolved to `null` rather than thrown: "no credentials stored yet" is the starting
 * state of every newly registered provider, and surfacing it as an error would put a red panel on
 * a screen where nothing has gone wrong. Retrying it would also be pointless — the answer will
 * not change until someone writes credentials.
 */
export function useCredentials(providerId: string | null) {
  return useQuery<ProviderCredentialsResponse | null>({
    queryKey: queryKeys.credentials.detail(providerId ?? ''),
    queryFn: async () => {
      try {
        return await credentialsApi.get(providerId!);
      } catch (error) {
        if (ApiError.isNotFound(error)) return null;
        throw error;
      }
    },
    enabled: Boolean(providerId),
    staleTime: 30_000,
  });
}

export function useStoreCredentials(providerId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: ProviderCredentialsRequest) => credentialsApi.store(providerId, body),
    onSuccess: (summary) => {
      // The response is the new summary, so write it straight in. Deliberately no optimistic
      // update: reporting a secret as stored before the server confirms it is exactly the lie an
      // admin would act on by enabling the provider.
      queryClient.setQueryData(queryKeys.credentials.detail(providerId), summary);
      toast.success('Credentials replaced', {
        description: 'Refresh the provider session to prove the new secrets authenticate.',
      });
    },
  });
}
