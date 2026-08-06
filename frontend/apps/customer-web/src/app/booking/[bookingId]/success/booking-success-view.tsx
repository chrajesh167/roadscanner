'use client';

import * as React from 'react';
import Link from 'next/link';
import { motion, useReducedMotion } from 'framer-motion';
import { ArrowRight, Check, Clock3, Ticket } from 'lucide-react';
import { Badge, statusTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState, PageLoader } from '@/components/ui/feedback';
import { Separator } from '@/components/ui/misc';
import { FadeIn } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { FlowSteps } from '@/components/booking/flow-steps';
import { useBooking } from '@/lib/hooks/use-bookings';
import { useBookingFlowStore } from '@/lib/store/booking-flow-store';
import { formatMoney, humanizeEnum, shortId } from '@/lib/utils/format';

/** A single celebratory tick — the only decorative animation in the flow. */
function SuccessMark() {
  const reduced = useReducedMotion();

  return (
    <motion.div
      initial={reduced ? { opacity: 0 } : { scale: 0.6, opacity: 0 }}
      animate={reduced ? { opacity: 1 } : { scale: 1, opacity: 1 }}
      transition={{ duration: 0.55, ease: [0.34, 1.56, 0.64, 1] }}
      className="grid size-16 place-items-center rounded-full border border-success/25 bg-success-soft"
    >
      <motion.span
        initial={reduced ? undefined : { pathLength: 0 }}
        animate={reduced ? undefined : { pathLength: 1 }}
        transition={{ delay: 0.25, duration: 0.4 }}
      >
        <Check className="size-7 text-success" aria-hidden />
      </motion.span>
    </motion.div>
  );
}

export function BookingSuccessView({ bookingId }: { bookingId: string }) {
  const { data: booking, isLoading, isError, error, refetch } = useBooking(bookingId);
  const resetFlow = useBookingFlowStore((state) => state.reset);

  // The flow is finished; clear it so a later visit doesn't resume a stale hold.
  React.useEffect(() => {
    if (booking) resetFlow();
  }, [booking, resetFlow]);

  if (isLoading) return <PageLoader label="Confirming your booking" />;

  if (isError || !booking) {
    return (
      <PageShell title="Booking">
        <ErrorState error={error} onRetry={() => void refetch()} />
      </PageShell>
    );
  }

  const confirmed = booking.status === 'CONFIRMED';

  return (
    <PageShell width="default">
      <FlowSteps current="Confirmed" />

      <FadeIn className="flex flex-col items-center gap-5 py-6 text-center">
        <SuccessMark />

        <div className="flex flex-col gap-2">
          <h1 className="text-h1">
            {confirmed ? "You're booked" : 'Booking created'}
          </h1>
          <p className="max-w-md text-body text-content-secondary">
            {confirmed
              ? 'Your seats are confirmed. The details are below and in My bookings.'
              : /* PENDING_PAYMENT here means the gateway has not confirmed yet — the booking is
                   real, it is simply not settled, and saying so is better than implying failure. */
                'Your seats are reserved. This page will show as confirmed once your payment settles with the gateway.'}
          </p>
        </div>

        <Badge tone={statusTone(booking.status)} size="md">
          {humanizeEnum(booking.status)}
        </Badge>
      </FadeIn>

      <FadeIn delay={0.12}>
        <Card variant="elevated" padding="lg" className="mt-4 flex flex-col gap-6">
          <div className="flex flex-wrap items-baseline justify-between gap-3">
            <div>
              <p className="text-micro uppercase text-content-muted">Booking reference</p>
              <p className="mt-1 font-mono text-h3">{shortId(booking.bookingId)}</p>
            </div>
            <div className="text-right">
              <p className="text-micro uppercase text-content-muted">Total paid</p>
              <p className="mt-1 text-h3 tabular-nums">
                {formatMoney(booking.fareAmount, booking.fareCurrency)}
              </p>
            </div>
          </div>

          <Separator />

          <div>
            <p className="mb-3 text-micro uppercase text-content-muted">
              {booking.passengers.length === 1 ? 'Passenger' : 'Passengers'}
            </p>
            <ul className="flex flex-col gap-2.5">
              {booking.passengers.map((passenger) => (
                <li
                  key={`${passenger.seatNumber}-${passenger.fullName}`}
                  className="flex items-center justify-between gap-3"
                >
                  <span className="min-w-0 truncate text-body text-content">
                    {passenger.fullName}
                    <span className="ml-2 text-caption capitalize text-content-muted">
                      {passenger.gender.toLowerCase()}
                    </span>
                  </span>
                  <Badge tone="accent">Seat {passenger.seatNumber}</Badge>
                </li>
              ))}
            </ul>
          </div>

          {!confirmed && (
            <p className="flex items-start gap-2 rounded-md border border-warning/20 bg-warning-soft p-3.5 text-caption text-warning">
              <Clock3 className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              Settlement is handled by the payment gateway and can take a moment. You don&apos;t
              need to pay again — check My bookings for the latest status.
            </p>
          )}

          <div className="flex flex-col gap-3 sm:flex-row">
            <Button full asChild>
              <Link href={`/bookings/${booking.bookingId}`}>
                <Ticket />
                View booking
              </Link>
            </Button>
            <Button variant="secondary" full asChild>
              <Link href="/search">
                Book another trip
                <ArrowRight />
              </Link>
            </Button>
          </div>
        </Card>
      </FadeIn>
    </PageShell>
  );
}
