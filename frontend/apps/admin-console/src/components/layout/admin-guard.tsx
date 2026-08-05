'use client';

import * as React from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { PageLoader } from '@/components/ui/feedback';
import { useLogout, useSession } from '@/lib/hooks/use-auth';

/**
 * Gates every console route on an authenticated `ADMIN` session.
 *
 * <p>The role is checked here as well as at the backend, which enforces it independently on every
 * `/api/v1/providers/**` route. This is not the security boundary — it is there so a signed-in
 * traveller who reaches this app is told what is wrong instead of walking into a wall of 403s
 * with no explanation.
 *
 * <p>A wrong-role session is shown a dead end with a sign-out, not a redirect: bouncing someone
 * back to a login page they are already authenticated against is a loop they cannot break.
 */
export function AdminGuard({ children }: { children: React.ReactNode }) {
  return (
    <React.Suspense fallback={<PageLoader label="Checking your session" />}>
      <AdminGuardInner>{children}</AdminGuardInner>
    </React.Suspense>
  );
}

function AdminGuardInner({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { session, hydrated, isAdmin } = useSession();

  React.useEffect(() => {
    if (!hydrated || session !== null) return;

    const query = searchParams.toString();
    const next = encodeURIComponent(query ? `${pathname}?${query}` : pathname);
    router.replace(`/login?next=${next}`);
  }, [hydrated, session, pathname, searchParams, router]);

  if (!hydrated) return <PageLoader label="Checking your session" />;
  if (session === null) return <PageLoader label="Redirecting to sign in" />;
  if (!isAdmin) return <NotAnAdmin role={session.role} />;

  return <>{children}</>;
}

function NotAnAdmin({ role }: { role: string }) {
  const logout = useLogout();

  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-5 px-6 py-24 text-center">
      <div
        className="grid size-14 place-items-center rounded-full border border-warning/25 bg-warning-soft text-warning [&_svg]:size-6"
        aria-hidden
      >
        <ShieldAlert />
      </div>
      <div className="flex flex-col gap-2">
        <h1 className="text-h2">This console is for administrators</h1>
        <p className="text-body text-content-secondary">
          You&apos;re signed in with the <strong className="text-content">{role}</strong> role. The
          provider registry requires <strong className="text-content">ADMIN</strong>, which another
          administrator grants through auth-service.
        </p>
      </div>
      <Button variant="secondary" onClick={() => logout.mutate(undefined)} loading={logout.isPending}>
        Sign out
      </Button>
    </div>
  );
}
