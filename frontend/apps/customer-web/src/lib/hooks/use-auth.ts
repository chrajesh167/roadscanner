'use client';

import { useRouter } from 'next/navigation';
import { useMutation } from '@tanstack/react-query';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { authEndpoints } from '@/lib/api/endpoints/auth';
import { useAuthStore } from '@/lib/store/auth-store';
import type { LoginRequest, RegisterRequest } from '@/lib/api/types';

/** The label the backend stores against the session, shown on the Settings screen. */
function deviceLabel(): string {
  if (typeof navigator === 'undefined') return 'RoadScanner Web';
  const ua = navigator.userAgent;
  const platform = /Mac/i.test(ua)
    ? 'macOS'
    : /Windows/i.test(ua)
      ? 'Windows'
      : /Android/i.test(ua)
        ? 'Android'
        : /iPhone|iPad/i.test(ua)
          ? 'iOS'
          : 'Web';
  return `RoadScanner Web · ${platform}`;
}

export function useSession() {
  const session = useAuthStore((s) => s.session);
  const hydrated = useAuthStore((s) => s.hydrated);
  return {
    session,
    hydrated,
    isAuthenticated: session !== null,
    isTraveler: session?.role === 'TRAVELER',
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

export function useRegister() {
  const setSession = useAuthStore((s) => s.setSession);
  return useMutation({
    mutationFn: (body: Omit<RegisterRequest, 'deviceLabel'>) =>
      authEndpoints.register({ ...body, deviceLabel: deviceLabel() }),
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
    // Local sign-out must succeed even if revocation fails — otherwise a network blip strands the
    // user in a session they've asked to leave. The server-side token still expires on its own.
    onSettled: () => {
      clear();
      queryClient.clear();
      router.push('/');
    },
    onError: () => toast.warning('Signed out locally', {
      description: "We couldn't reach the server to revoke this session.",
    }),
  });
}
