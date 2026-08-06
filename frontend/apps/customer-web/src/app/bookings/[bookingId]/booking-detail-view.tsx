'use client';

import * as React from 'react';
import Link from 'next/link';
import { toast } from 'sonner';
import { CreditCard, Download, Ticket, XCircle } from 'lucide-react';
import { Badge, statusTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ErrorState, PageLoader } from '@/components/ui/feedback';
import { Separator, TBody, TD, TH, THead, TR, Table } from '@/components/ui/misc';
import { FadeIn } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { useBooking, useCancelBooking, useTicket } from '@/lib/hooks/use-bookings';
import {
  formatDateTime,
  formatFullDate,
  formatMoney,
  humanizeEnum,
  shortId,
} from '@/lib/utils/format';

/** Cancellable states: anything not already terminal. */
const CANCELLABLE = new Set(['PENDING_PAYMENT', 'CONFIRMED']);

export function BookingDetailView({ bookingId }: { bookingId: string }) {
  const { data: booking, isLoading, isError, error, refetch } = useBooking(bookingId);
  const cancelBooking = useCancelBooking();
  const [cancelOpen, setCancelOpen] = React.useState(false);

  // A ticket only exists once confirmed; asking earlier is a guaranteed 404.
  const ticketEnabled = booking?.status === 'CONFIRMED';
  const { data: ticket } = useTicket(bookingId, ticketEnabled);

  function downloadTicket() {
    if (!ticket) return;

    // The backend base64-encodes the provider's raw ticket payload; decode to the original bytes
    // rather than re-wrapping it, so the file is exactly what the provider issued.
    const binary = atob(ticket.contentBase64);
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    const blob = new Blob([bytes], { type: ticket.format || 'application/octet-stream' });

    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `roadscanner-ticket-${shortId(bookingId)}`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function confirmCancel() {
    cancelBooking.mutate(bookingId, {
      onSuccess: (result) => {
        setCancelOpen(false);
        toast.success('Booking cancelled', {
          description: `This booking is now ${humanizeEnum(result.status).toLowerCase()}.`,
        });
      },
      onError: (cancelError) =>
        toast.error('Could not cancel', {
          description: cancelError instanceof Error ? cancelError.message : 'Please try again.',
        }),
    });
  }

  if (isLoading) return <PageLoader label="Loading your booking" />;

  if (isError || !booking) {
    return (
      <PageShell backHref="/bookings" title="Booking">
        <ErrorState error={error} onRetry={() => void refetch()} />
      </PageShell>
    );
  }

  const timeline = [
    ['Created', booking.createdAt],
    ['Confirmed', booking.confirmedAt],
    ['Cancelled', booking.cancelledAt],
    ['Completed', booking.completedAt],
  ].filter(([, at]) => Boolean(at)) as Array<[string, string]>;

  return (
    <PageShell
      backHref="/bookings"
      backLabel="My bookings"
      title={shortId(booking.bookingId)}
      description={`Booked ${formatDateTime(booking.createdAt)}`}
      actions={
        <Badge tone={statusTone(booking.status)} size="md">
          {humanizeEnum(booking.status)}
        </Badge>
      }
    >
      <div className="flex flex-col gap-6">
        {booking.status === 'PENDING_PAYMENT' && (
          <FadeIn>
            <Card
              padding="md"
              className="flex flex-col gap-4 border-warning/25 bg-warning-soft sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <p className="text-body font-medium text-warning">Payment not settled</p>
                <p className="mt-1 text-caption text-content-secondary">
                  This booking is reserved but not yet confirmed.
                </p>
              </div>
              <Button size="sm" asChild>
                <Link href={`/booking/${booking.bookingId}/payment`}>
                  <CreditCard />
                  Complete payment
                </Link>
              </Button>
            </Card>
          </FadeIn>
        )}

        <FadeIn delay={0.06}>
          <Card padding="lg">
            <h2 className="text-h3">Passengers</h2>
            <div className="mt-4 -mx-4">
              <Table>
                <THead>
                  <TR>
                    <TH>Name</TH>
                    <TH>Date of birth</TH>
                    <TH>Gender</TH>
                    <TH>Seat</TH>
                  </TR>
                </THead>
                <TBody>
                  {booking.passengers.map((passenger) => (
                    <TR key={`${passenger.seatNumber}-${passenger.fullName}`}>
                      <TD className="text-content">{passenger.fullName}</TD>
                      <TD className="tabular-nums">{formatFullDate(passenger.birthDate)}</TD>
                      <TD className="capitalize">{passenger.gender.toLowerCase()}</TD>
                      <TD>
                        <Badge tone="accent">{passenger.seatNumber}</Badge>
                      </TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </div>
          </Card>
        </FadeIn>

        <div className="grid gap-6 sm:grid-cols-2">
          <FadeIn delay={0.1}>
            <Card padding="lg" className="h-full">
              <h2 className="text-h3">Payment</h2>
              <div className="mt-4 flex items-baseline justify-between">
                <span className="text-body text-content-secondary">Total fare</span>
                <span className="text-h3 tabular-nums">
                  {formatMoney(booking.fareAmount, booking.fareCurrency)}
                </span>
              </div>
              {booking.providerBookingReference && (
                <>
                  <Separator className="my-4" />
                  <p className="text-micro uppercase text-content-muted">Operator reference</p>
                  <p className="mt-1 font-mono text-caption text-content-secondary">
                    {booking.providerBookingReference}
                  </p>
                </>
              )}
              {booking.cancellationReason && (
                <>
                  <Separator className="my-4" />
                  <p className="text-micro uppercase text-content-muted">Cancellation reason</p>
                  <p className="mt-1 text-caption text-content-secondary">
                    {humanizeEnum(booking.cancellationReason)}
                  </p>
                </>
              )}
            </Card>
          </FadeIn>

          <FadeIn delay={0.14}>
            <Card padding="lg" className="h-full">
              <h2 className="text-h3">Timeline</h2>
              <ol className="mt-4 flex flex-col gap-4">
                {timeline.map(([label, at], index) => (
                  <li key={label} className="flex gap-3.5">
                    <span className="mt-1.5 flex flex-col items-center">
                      <span className="size-2 rounded-full bg-accent" />
                      {index < timeline.length - 1 && (
                        <span className="mt-1 h-6 w-px bg-line-strong" />
                      )}
                    </span>
                    <span>
                      <span className="block text-body text-content">{label}</span>
                      <span className="block text-caption text-content-muted">
                        {formatDateTime(at)}
                      </span>
                    </span>
                  </li>
                ))}
              </ol>
            </Card>
          </FadeIn>
        </div>

        <FadeIn delay={0.18}>
          <Card padding="lg" className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="flex items-center gap-2 text-h3">
                <Ticket className="size-4 text-content-muted" aria-hidden />
                Your ticket
              </h2>
              <p className="mt-1.5 text-caption text-content-secondary">
                {ticket
                  ? `Issued ${formatDateTime(ticket.issuedAt)}`
                  : ticketEnabled
                    ? "The operator hasn't issued a ticket for this booking yet."
                    : 'A ticket is issued once the booking is confirmed.'}
              </p>
            </div>

            <div className="flex flex-wrap gap-3">
              {ticket && (
                <Button variant="secondary" onClick={downloadTicket}>
                  <Download />
                  Download
                </Button>
              )}
              {CANCELLABLE.has(booking.status) && (
                <Button variant="danger" onClick={() => setCancelOpen(true)}>
                  <XCircle />
                  Cancel booking
                </Button>
              )}
            </div>
          </Card>
        </FadeIn>
      </div>

      <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cancel this booking?</DialogTitle>
            <DialogDescription>
              Seats {booking.passengers.map((p) => p.seatNumber).join(', ')} will be released. If
              payment was captured, a refund is initiated automatically — it can take a few days to
              reach your account.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="secondary">Keep booking</Button>
            </DialogClose>
            <Button
              variant="danger"
              loading={cancelBooking.isPending}
              loadingText="Cancelling"
              onClick={confirmCancel}
            >
              Cancel booking
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageShell>
  );
}
