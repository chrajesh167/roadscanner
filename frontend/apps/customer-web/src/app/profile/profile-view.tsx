'use client';

import Link from 'next/link';
import { Info, Settings2, ShieldCheck, Ticket, Wallet } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/feedback';
import { Avatar, Separator } from '@/components/ui/misc';
import { FadeIn } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { useSession } from '@/lib/hooks/use-auth';
import { useBookings } from '@/lib/hooks/use-bookings';
import { formatMoney } from '@/lib/utils/format';

export function ProfileView() {
  const { session } = useSession();
  const { data: bookings, isLoading } = useBookings();

  const stats = (() => {
    if (!bookings) return null;
    const confirmed = bookings.filter((booking) => booking.status === 'CONFIRMED');
    const currency = bookings[0]?.fareCurrency ?? 'INR';
    const spent = confirmed.reduce(
      (sum, booking) => sum + Number.parseFloat(booking.fareAmount || '0'),
      0,
    );
    const seats = confirmed.reduce((sum, booking) => sum + booking.passengers.length, 0);
    return { trips: confirmed.length, seats, spent, currency };
  })();

  const displayName = session?.identifier ?? 'Traveller';

  return (
    <PageShell
      title="Profile"
      description="Your account and travel history at a glance."
      actions={
        <Button variant="secondary" size="sm" asChild>
          <Link href="/settings">
            <Settings2 />
            Settings
          </Link>
        </Button>
      }
    >
      <div className="flex flex-col gap-6">
        <FadeIn>
          <Card variant="elevated" padding="lg">
            <div className="flex flex-wrap items-center gap-5">
              <Avatar name={displayName} className="size-16 text-h3" />
              <div className="min-w-0">
                <p className="truncate text-h3">{displayName}</p>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  {session?.role && (
                    <Badge tone="accent">
                      <ShieldCheck />
                      {session.role.toLowerCase()}
                    </Badge>
                  )}
                  <span className="font-mono text-micro text-content-muted">
                    {session?.userId}
                  </span>
                </div>
              </div>
            </div>
          </Card>
        </FadeIn>

        {/* Stats derived from booking history — no separate stats endpoint exists. */}
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            { label: 'Trips taken', value: stats?.trips, icon: Ticket },
            { label: 'Seats booked', value: stats?.seats, icon: Ticket },
            {
              label: 'Total spent',
              value: stats ? formatMoney(stats.spent, stats.currency) : undefined,
              icon: Wallet,
            },
          ].map((stat, index) => (
            <FadeIn key={stat.label} delay={0.06 + index * 0.05}>
              <Card padding="lg" className="h-full">
                <stat.icon className="mb-3 size-4 text-content-muted" aria-hidden />
                <p className="text-micro uppercase text-content-muted">{stat.label}</p>
                {isLoading ? (
                  <Skeleton className="mt-2 h-7 w-20" />
                ) : (
                  <p className="mt-1.5 text-h2 tabular-nums">{stat.value ?? '—'}</p>
                )}
              </Card>
            </FadeIn>
          ))}
        </div>

        <FadeIn delay={0.2}>
          <Card padding="lg">
            <h2 className="text-h3">Account details</h2>
            <Separator className="my-5" />

            <dl className="flex flex-col gap-4">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <dt className="text-body text-content-secondary">Sign-in identifier</dt>
                <dd className="text-body text-content">{session?.identifier ?? 'Not recorded'}</dd>
              </div>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <dt className="text-body text-content-secondary">User ID</dt>
                <dd className="font-mono text-caption text-content-secondary">{session?.userId}</dd>
              </div>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <dt className="text-body text-content-secondary">Role</dt>
                <dd className="text-body text-content">{session?.role}</dd>
              </div>
            </dl>

            {/*
              Honest about scope: `user-service` owns Profile Management in the architecture but
              exposes no endpoints yet, so there is genuinely nothing to edit against.
            */}
            <p className="mt-6 flex items-start gap-2.5 rounded-md border border-line bg-white/[0.02] p-4 text-caption text-content-secondary">
              <Info className="mt-0.5 size-3.5 shrink-0 text-content-muted" aria-hidden />
              Editable profile details — name, contact details and saved passengers — arrive with
              user-service. Until then, this reflects what your sign-in session knows.
            </p>
          </Card>
        </FadeIn>
      </div>
    </PageShell>
  );
}
