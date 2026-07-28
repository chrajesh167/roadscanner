'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthTokensResponse, Role } from '@/lib/api/types';

export interface AuthSession {
  userId: string;
  role: Role;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  /**
   * The identifier the user typed at login. auth-service exposes no profile endpoint and the JWT
   * carries no email claim, so this is the only human-readable handle the client has.
   */
  identifier: string | null;
}

interface AuthState {
  session: AuthSession | null;
  /** False until localStorage has been read, so the header never flashes a signed-out state. */
  hydrated: boolean;
  setSession: (tokens: AuthTokensResponse, identifier?: string | null) => void;
  updateTokens: (tokens: AuthTokensResponse) => void;
  setHydrated: () => void;
  clear: () => void;
}

function toSession(tokens: AuthTokensResponse, identifier: string | null): AuthSession {
  return {
    userId: tokens.userId,
    role: tokens.role,
    accessToken: tokens.accessToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    refreshToken: tokens.refreshToken,
    refreshTokenExpiresAt: tokens.refreshTokenExpiresAt,
    identifier,
  };
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      session: null,
      hydrated: false,

      setSession: (tokens, identifier = null) => set({ session: toSession(tokens, identifier) }),

      /** Refresh rotates both tokens; the identifier survives the rotation. */
      updateTokens: (tokens) =>
        set({ session: toSession(tokens, get().session?.identifier ?? null) }),

      setHydrated: () => set({ hydrated: true }),

      clear: () => set({ session: null }),
    }),
    {
      name: 'roadscanner.auth',
      partialize: (state) => ({ session: state.session }),
      onRehydrateStorage: () => (state) => state?.setHydrated(),
    },
  ),
);

/** Non-React accessors, used by the axios interceptors. */
export function getSession(): AuthSession | null {
  return useAuthStore.getState().session;
}

export function clearSession(): void {
  useAuthStore.getState().clear();
}
