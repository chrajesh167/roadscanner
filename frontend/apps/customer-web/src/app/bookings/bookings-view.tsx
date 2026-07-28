'use client';

import * as React from 'react';
import Link from 'next/link';
import { ArrowRight, TicketCheck } from 'lucide-react';
import { Badge, statusTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { Stagger, StaggerItem } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { useBookings } from '@/lib/hooks/use-bookings';
import { formatDateTime, formatMoney, humanizeEnum, shortId } from '@/lib/utils/format';
import { cn } from '@/lib/utils/cn';
import type { BookingResponse } from '@/lib/api/types';

type Tab = 'ALL' | 'UPCOMING' | 'CANCELLED';

const TABS: ReadonlyArray<{ value: Tab; label: string }> = [
  { value: 'ALL', label: 'All' },
  { value: 'UPCOMING', label: 'Active' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

function matchesTab(booking: BookingResponse, tab: Tab): boolean {
  if (tab === 'ALL') return true;
  if (tab === 'CANCELLED') return booking.status === 'CANCELLED';
  return booking.status === 'CONFIRMED' || booking.status === 'PENDING_PAYMENT';
}

function BookingRow({ booking }: { booking: BookingResponse }) {
  const seats = booking.passengers.map((passenger) => passenger.seatNumber).join(', ');

  return (
    <Card variant="solid" interactive padding="none" className="group">
      <Link
        href={`/bookings/${booking.bookingId}`}
        className="flex flex-col gap-4 p-5 focus:outline-none sm:flex-row sm:items-center sm:justify-between sm:p-6"
      >
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2.5">
            <span className="font-mono text-[0.9375rem] font-medium text-content">
              {shortId(booking.bookingId)}
            </span>
            <Badge tone={statusTone(booking.status)}>{humanizeEnum(booking.status)}</Badge>
            {booking.supportFlagged && <Badge tone="warning">Support review</Badge>}
          </div>

          <p className="mt-2 text-caption text-content-secondary">
            {booking.passengers.length} {booking.passengers.length === 1 ? 'passenger' : 'passengers'}
            {seats && <span className="text-content-muted"> · Seats {seats}</span>}
          </p>
          <p className="mt-1 text-caption text-content-muted">
            Booked {formatDateTime(booking.createdAt)}
          </p>
        </div>

        <div className="flex items-center justify-between gap-4 sm:justify-end">
          <span className="text-h3 tabular-nums">
            {formatMoney(booking.fareAmount, booking.fareCurrency)}
          </span>
          <ArrowRight className="size-4 shrink-0 text-content-muted transition-transform duration-300 group-hover:translate-x-1 group-hover:text-accent" />
        </div>
      </Link>
    </Card>
  );
}

export function BookingsView() {
  const [tab, setTab] = React.useState<Tab>('ALL');
  const { data: bookings, isLoading, isError, error, refetch } = useBookings();

  const filtered = React.useMemo(
    () => (bookings ?? []).filter((booking) => matchesTab(booking, tab)),
    [bookings, tab],
  );

  return (
    <PageShell
      title="My bookings"
      description="Every trip you've booked, newest first."
      actions={
        <Button variant="secondary" size="sm" asChild>
          <Link href="/search">Book a trip</Link>
        </Button>
      }
    >
      {/* Tabs are a client-side view over one fetch — the backend returns the full history. */}
      <div
        role="tablist"
        aria-label="Filter bookings"
        className="mb-6 inline-flex gap-1 rounded-md border border-line bg-surface p-1"
      >
        {TABS.map((item) => (
          <button
            key={item.value}
            role="tab"
            aria-selected={tab === item.value}
            onClick={() => setTab(item.value)}
            className={cn(
              'rounded-sm px-4 py-1.5 text-caption transition-colors duration-200',
              tab === item.value
                ? 'bg-elevated text-content'
                : 'text-content-secondary hover:text-content',
            )}
          >
            {item.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-4">
          {Array.from({ length: 3 }).map((_, index) => (
            <Card key={index} padding="lg">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="mt-3 h-3 w-48" />
              <Skeleton className="mt-2 h-3 w-40" />
            </Card>
          ))}
        </div>
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<TicketCheck />}
          title={tab === 'ALL' ? 'No bookings yet' : 'Nothing here'}
          description={
            tab === 'ALL'
              ? 'When you book a trip it will appear here, along with your ticket.'
              : 'No bookings match this filter.'
          }
          action={
            tab === 'ALL' ? (
              <Button asChild>
                <Link href="/search">Find a trip</Link>
              </Button>
            ) : (
              <Button variant="secondary" onClick={() => setTab('ALL')}>
                Show all
              </Button>
            )
          }
        />
      ) : (
        <Stagger className="flex flex-col gap-4">
          {filtered.map((booking) => (
            <StaggerItem key={booking.bookingId}>
              <BookingRow booking={booking} />
            </StaggerItem>
          ))}
        </Stagger>
      )}
    </PageShell>
  );
}
