'use client';

import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { authEndpoints } from '@/lib/api/endpoints/auth';
import { useAuthStore } from '@/lib/store/auth-store';
import type { LoginRequest } from '@/lib/api/types';

function deviceLabel(): string {
  if (typeof navigator === 'undefined') return 'RoadScanner Admin';
  const ua = navigator.userAgent;
  const platform = /Mac/i.test(ua)
    ? 'macOS'
    : /Windows/i.test(ua)
      ? 'Windows'
      : /Linux/i.test(ua)
        ? 'Linux'
        : 'Web';
  return `RoadScanner Admin · ${platform}`;
}

export function useSession() {
  const session = useAuthStore((s) => s.session);
  const hydrated = useAuthStore((s) => s.hydrated);
  return {
    session,
    hydrated,
    isAuthenticated: session !== null,
    isAdmin: session?.role === 'ADMIN',
  };
}

export function useLogin() {
  const setSession = useAuthStore((s) => s.setSession);
  return useMutation({
    mutationFn: (body: Omit<LoginRequest, 'deviceLabel'>) =>
      authEndpoints.login({ ...body, deviceLabel: deviceLabel() }),
    onSuccess: (tokens, variables) => setSession(tokens, variables.identifier),
  });
}

export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const clear = useAuthStore((s) => s.clear);

  return useMutation({
    mutationFn: async (options?: { allDevices?: boolean }) => {
      const session = useAuthStore.getState().session;
      if (!session) return;
      if (options?.allDevices) {
        await authEndpoints.logoutAll();
      } else {
        await authEndpoints.logout(session.refreshToken);
      }
    },
    // Local sign-out must succeed even if revocation fails — otherwise a network blip strands an
    // admin in a session they've asked to leave. The server-side token still expires on its own.
    onSettled: () => {
      clear();
      queryClient.clear();
      router.push('/login');
    },
    onError: () =>
      toast.warning('Signed out locally', {
        description: "We couldn't reach auth-service to revoke this session.",
      }),
  });
}
