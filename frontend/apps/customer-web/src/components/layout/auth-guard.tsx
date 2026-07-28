'use client';

import * as React from 'react';
import { useRouter, usePathname, useSearchParams } from 'next/navigation';
import { PageLoader } from '@/components/ui/feedback';
import { useSession } from '@/lib/hooks/use-auth';

/**
 * Gates a route on an authenticated TRAVELER session.
 *
 * The booking, payment and account routes are TRAVELER-only at the backend — those controllers
 * reject any other role outright — so the guard checks the role too rather than letting the user
 * walk into a guaranteed 403.
 *
 * Redirects carry a `next` param so the user lands back where they were aiming after signing in.
 */
export function AuthGuard({
  children,
  requireTraveler = true,
}: {
  children: React.ReactNode;
  requireTraveler?: boolean;
}) {
  // The guard reads `useSearchParams` to build the post-login `next` target, which opts the tree
  // into client rendering. Owning the Suspense boundary here means no guarded page has to
  // remember to add one.
  return (
    <React.Suspense fallback={<PageLoader label="Checking your session" />}>
      <AuthGuardInner requireTraveler={requireTraveler}>{children}</AuthGuardInner>
    </React.Suspense>
  );
}

function AuthGuardInner({
  children,
  requireTraveler,
}: {
  children: React.ReactNode;
  requireTraveler: boolean;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { session, hydrated, isTraveler } = useSession();

  const allowed = session !== null && (!requireTraveler || isTraveler);

  React.useEffect(() => {
    if (!hydrated || allowed) return;

    const query = searchParams.toString();
    const next = encodeURIComponent(query ? `${pathname}?${query}` : pathname);
    router.replace(session === null ? `/login?next=${next}` : '/');
  }, [hydrated, allowed, session, pathname, searchParams, router]);

  if (!hydrated) return <PageLoader label="Checking your session" />;
  if (!allowed) return <PageLoader label="Redirecting" />;

  return <>{children}</>;
}
