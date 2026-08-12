import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminGuard } from './admin-guard';
import { useAuthStore } from '@/lib/store/auth-store';
import type { AuthSession } from '@/lib/store/auth-store';
import type { Role } from '@/lib/api/types';

/**
 * The guard's three terminal states, pinned.
 *
 * <p>Written after a stuck "Checking your session" turned out to be a stale dev server serving
 * HTML whose client chunks had been deleted by a production build — the guard never hydrated
 * because no JavaScript ran at all. Nothing here would have caught that (no test can catch a
 * bundle that is never fetched), but the investigation showed the guard's own contract had no
 * coverage, so a real regression in it would have looked identical from the browser.
 *
 * <p>These assertions are about the gate, not the shell: that an unauthenticated visitor is sent
 * to sign-in, that a non-ADMIN role is stopped rather than shown the console, and that the
 * pre-hydration state is a loader rather than a flash of either.
 */

const replace = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  usePathname: () => '/dashboard',
  useSearchParams: () => new URLSearchParams(''),
}));

function sessionWithRole(role: Role): AuthSession {
  return {
    userId: '00000000-0000-0000-0000-000000000001',
    role,
    accessToken: 'header.payload.signature',
    accessTokenExpiresAt: '2099-01-01T00:00:00Z',
    refreshToken: 'refresh-token',
    refreshTokenExpiresAt: '2099-01-01T00:00:00Z',
    identifier: 'admin@roadscanner.com',
  };
}

/** Drives the store the way persist's rehydration callback does at startup. */
function setStoreState(session: AuthSession | null, hydrated: boolean) {
  act(() => {
    useAuthStore.setState({ session, hydrated });
  });
}

/** The wrong-role branch renders a sign-out button, which is a react-query mutation. */
function renderGuard() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <AdminGuard>
        <p>console</p>
      </AdminGuard>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  // Unmount before resetting: this hook runs ahead of the auto-cleanup, so a bare store write here
  // would re-render a tree the next test never asked for.
  cleanup();
  replace.mockClear();
  setStoreState(null, false);
});

describe('AdminGuard', () => {
  it('shows the session loader until the store has hydrated', () => {
    setStoreState(null, false);

    renderGuard();

    // Neither the console nor a redirect: until localStorage has been read, "signed out" is not
    // yet a fact, and acting on it would bounce a signed-in admin to the login page on every load.
    expect(screen.getByText(/checking your session/i)).toBeInTheDocument();
    expect(screen.queryByText('console')).toBeNull();
    expect(replace).not.toHaveBeenCalled();
  });

  it('redirects an unauthenticated visitor to sign-in, carrying where they were headed', async () => {
    setStoreState(null, true);

    renderGuard();

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith(`/login?next=${encodeURIComponent('/dashboard')}`),
    );
    expect(screen.queryByText('console')).toBeNull();
  });

  it('refuses a signed-in non-ADMIN role instead of rendering the console', () => {
    setStoreState(sessionWithRole('TRAVELER'), true);

    renderGuard();

    expect(screen.getByText(/this console is for administrators/i)).toBeInTheDocument();
    expect(screen.queryByText('console')).toBeNull();
    // A dead end with a sign-out, never a redirect: bouncing someone back to a login page they are
    // already authenticated against is a loop they cannot break.
    expect(replace).not.toHaveBeenCalled();
  });

  it('renders the console for a hydrated ADMIN session', () => {
    setStoreState(sessionWithRole('ADMIN'), true);

    renderGuard();

    expect(screen.getByText('console')).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
