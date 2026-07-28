'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import { AnimatePresence, motion } from 'framer-motion';
import { toast } from 'sonner';
import {
  Building2,
  CheckCircle2,
  CreditCard,
  Landmark,
  Lock,
  Smartphone,
  Wallet,
  XCircle,
} from 'lucide-react';
import { Badge, statusTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState, PageLoader, Spinner } from '@/components/ui/feedback';
import { Separator } from '@/components/ui/misc';
import { FadeIn, Pressable } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { FlowSteps } from '@/components/booking/flow-steps';
import { useBooking } from '@/lib/hooks/use-bookings';
import {
  isTerminalPaymentStatus,
  useInitiatePayment,
  usePaymentStatus,
} from '@/lib/hooks/use-payment';
import { useBookingFlowStore } from '@/lib/store/booking-flow-store';
import { formatMoney, humanizeEnum } from '@/lib/utils/format';
import { cn } from '@/lib/utils/cn';
import type { PaymentMethod } from '@/lib/api/types';

const METHODS: ReadonlyArray<{
  value: PaymentMethod;
  label: string;
  hint: string;
  icon: typeof CreditCard;
}> = [
  { value: 'UPI', label: 'UPI', hint: 'Pay from any UPI app', icon: Smartphone },
  { value: 'CARD', label: 'Card', hint: 'Credit or debit', icon: CreditCard },
  { value: 'NETBANKING', label: 'Net banking', hint: 'Straight from your bank', icon: Landmark },
  { value: 'WALLET', label: 'Wallet', hint: 'Your saved balance', icon: Wallet },
];

export function PaymentView({ bookingId }: { bookingId: string }) {
  const router = useRouter();
  const [method, setMethod] = React.useState<PaymentMethod>('UPI');
  const [paymentId, setPaymentId] = React.useState<string | null>(null);

  const { data: booking, isLoading, isError, error, refetch } = useBooking(bookingId);
  const initiatePayment = useInitiatePayment();
  const { data: paymentStatus } = usePaymentStatus(paymentId);

  const idempotencyKey = useBookingFlowStore((state) => state.idempotencyKey);
  const storedBookingId = useBookingFlowStore((state) => state.bookingId);

  const status = paymentStatus?.status;
  const settled = isTerminalPaymentStatus(status);

  // A captured payment is the end of this screen's job — hand off to the confirmation page.
  React.useEffect(() => {
    if (status === 'CAPTURED') {
      const timer = setTimeout(() => router.replace(`/booking/${bookingId}/success`), 900);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [status, bookingId, router]);

  function pay() {
    if (!booking) return;

    // Reuse the key minted when the booking was created: retrying the same intent must never
    // produce a second charge. A key is only absent when this page was opened cold (e.g. a
    // direct link), in which case one deterministic in the booking id is good enough.
    const key = storedBookingId === bookingId && idempotencyKey ? idempotencyKey : `booking-${bookingId}`;

    initiatePayment.mutate(
      {
        body: {
          bookingReference: bookingId,
          amount: booking.fareAmount,
          currency: booking.fareCurrency,
          method,
        },
        idempotencyKey: key,
      },
      {
        onSuccess: (result) => setPaymentId(result.paymentId),
        onError: (mutationError) =>
          toast.error('Could not start the payment', {
            description:
              mutationError instanceof Error ? mutationError.message : 'Please try again.',
          }),
      },
    );
  }

  if (isLoading) return <PageLoader label="Loading your booking" />;

  if (isError || !booking) {
    return (
      <PageShell title="Payment">
        <ErrorState
          error={error}
          title="We couldn't load this booking"
          onRetry={() => void refetch()}
        />
      </PageShell>
    );
  }

  // Already settled — no payment to take.
  if (booking.status === 'CONFIRMED') {
    return (
      <PageShell title="Payment">
        <Card padding="lg" className="flex flex-col items-center gap-4 text-center">
          <CheckCircle2 className="size-10 text-success" aria-hidden />
          <h2 className="text-h3">This booking is already confirmed</h2>
          <Button onClick={() => router.replace(`/bookings/${bookingId}`)}>View booking</Button>
        </Card>
      </PageShell>
    );
  }

  return (
    <PageShell
      width="wide"
      title="Payment"
      description={`Booking ${booking.bookingId.split('-')[0]?.toUpperCase()}`}
    >
      <FlowSteps current="Payment" />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
        <div className="flex flex-col gap-6">
          <FadeIn>
            <Card padding="lg">
              <h2 className="text-h3">How would you like to pay?</h2>

              <div
                role="radiogroup"
                aria-label="Payment method"
                className="mt-5 grid gap-3 sm:grid-cols-2"
              >
                {METHODS.map((option) => {
                  const active = method === option.value;
                  return (
                    <Pressable
                      key={option.value}
                      onClick={() => setMethod(option.value)}
                      disabled={Boolean(paymentId)}
                      ariaPressed={active}
                      ariaLabel={option.label}
                      className={cn(
                        'flex items-center gap-3.5 rounded-md border p-4 text-left transition-colors duration-200',
                        active
                          ? 'border-accent bg-accent-soft'
                          : 'border-line bg-elevated hover:border-line-strong',
                        paymentId && 'opacity-60',
                      )}
                    >
                      <span
                        className={cn(
                          'grid size-9 shrink-0 place-items-center rounded-sm border',
                          active
                            ? 'border-accent/30 bg-accent/15 text-accent-text'
                            : 'border-line bg-white/[0.03] text-content-muted',
                        )}
                      >
                        <option.icon className="size-4" aria-hidden />
                      </span>
                      <span className="min-w-0">
                        <span className="block text-body font-medium text-content">
                          {option.label}
                        </span>
                        <span className="block text-caption text-content-muted">{option.hint}</span>
                      </span>
                    </Pressable>
                  );
                })}
              </div>
            </Card>
          </FadeIn>

          {/* Live payment state, once initiated */}
          <AnimatePresence>
            {paymentId && (
              <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
              >
                <Card padding="lg" variant="elevated">
                  <div className="flex items-start gap-4">
                    <span className="mt-0.5">
                      {status === 'CAPTURED' ? (
                        <CheckCircle2 className="size-5 text-success" aria-hidden />
                      ) : settled ? (
                        <XCircle className="size-5 text-danger" aria-hidden />
                      ) : (
                        <Spinner />
                      )}
                    </span>

                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2.5">
                        <h2 className="text-h3">
                          {status === 'CAPTURED'
                            ? 'Payment confirmed'
                            : settled
                              ? 'Payment did not go through'
                              : 'Waiting for your bank'}
                        </h2>
                        {status && <Badge tone={statusTone(status)}>{humanizeEnum(status)}</Badge>}
                      </div>

                      <p className="mt-2 text-body text-content-secondary">
                        {status === 'CAPTURED'
                          ? 'Your seats are confirmed. Taking you to your ticket.'
                          : settled
                            ? 'No money has been taken. You can try again with a different method.'
                            : /* The gateway confirms out-of-band via webhook — the client cannot
                                 advance this itself, so say plainly what is being waited on. */
                              'Your payment has been sent to the gateway. This page updates itself the moment the gateway confirms — you do not need to refresh.'}
                      </p>

                      <p className="mt-3 font-mono text-micro text-content-muted">
                        Payment {paymentId}
                      </p>

                      {settled && status !== 'CAPTURED' && (
                        <Button
                          variant="secondary"
                          size="sm"
                          className="mt-4"
                          onClick={() => setPaymentId(null)}
                        >
                          Try another method
                        </Button>
                      )}
                    </div>
                  </div>
                </Card>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Order summary */}
        <FadeIn delay={0.1} className="lg:sticky lg:top-24">
          <Card variant="elevated" padding="lg" className="flex flex-col gap-5">
            <h2 className="text-h3">Order summary</h2>

            <ul className="flex flex-col gap-2.5">
              {booking.passengers.map((passenger) => (
                <li
                  key={`${passenger.seatNumber}-${passenger.fullName}`}
                  className="flex items-center justify-between gap-3 text-body"
                >
                  <span className="min-w-0 truncate text-content-secondary">
                    {passenger.fullName}
                  </span>
                  <Badge tone="neutral">Seat {passenger.seatNumber}</Badge>
                </li>
              ))}
            </ul>

            <Separator />

            <div className="flex items-baseline justify-between">
              <span className="text-body text-content-secondary">Amount payable</span>
              <span className="text-h3 tabular-nums">
                {formatMoney(booking.fareAmount, booking.fareCurrency)}
              </span>
            </div>

            <Button
              size="lg"
              full
              disabled={Boolean(paymentId)}
              loading={initiatePayment.isPending}
              loadingText="Starting payment"
              onClick={pay}
            >
              <Lock />
              Pay {formatMoney(booking.fareAmount, booking.fareCurrency)}
            </Button>

            <p className="flex items-start gap-2 text-caption text-content-muted">
              <Building2 className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              Payments are processed by our gateway partner. RoadScanner never sees your card
              details.
            </p>
          </Card>
        </FadeIn>
      </div>
    </PageShell>
  );
}
