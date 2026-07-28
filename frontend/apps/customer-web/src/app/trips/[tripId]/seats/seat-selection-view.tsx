'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import { AnimatePresence, motion } from 'framer-motion';
import { toast } from 'sonner';
import { ArmchairIcon, ArrowRight, Info } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { Separator } from '@/components/ui/misc';
import { FadeIn } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { FlowSteps } from '@/components/booking/flow-steps';
import { SeatMap, SELECTABLE_STATUS } from '@/components/booking/seat-map';
import { useHoldSeats, useSeatView } from '@/lib/hooks/use-bookings';
import { useTripDetail } from '@/lib/hooks/use-search';
import { useBookingFlowStore } from '@/lib/store/booking-flow-store';
import { ApiError } from '@/lib/api/client';
import { formatMoney } from '@/lib/utils/format';

const MAX_SEATS = 6;

export function SeatSelectionView({ tripId }: { tripId: string }) {
  const router = useRouter();
  const [selected, setSelected] = React.useState<string[]>([]);

  const { data: seatView, isLoading, isError, error, refetch } = useSeatView(tripId);
  const { data: trip } = useTripDetail(tripId);
  const holdSeats = useHoldSeats();

  const startFlow = useBookingFlowStore((state) => state.startFlow);
  const setHold = useBookingFlowStore((state) => state.setHold);
  const storedTrip = useBookingFlowStore((state) => state.trip);

  // Deep-linking straight here (or a refresh) leaves the flow without its trip — seed it.
  React.useEffect(() => {
    if (trip && storedTrip?.tripId !== trip.tripId) startFlow(trip);
  }, [trip, storedTrip?.tripId, startFlow]);

  // Memoised so the fallback `[]` isn't a fresh array on every render, which would churn the
  // selected-seat memo below.
  const seats = React.useMemo(() => seatView?.seats ?? [], [seatView]);
  const availableCount = seats.filter((seat) => seat.status === SELECTABLE_STATUS).length;

  const selectedSeats = React.useMemo(
    () => seats.filter((seat) => selected.includes(seat.seatNumber)),
    [seats, selected],
  );

  const currency = selectedSeats[0]?.priceCurrency ?? trip?.fareCurrency ?? 'INR';
  const total = selectedSeats.reduce(
    (sum, seat) => sum + Number.parseFloat(seat.priceAmount || '0'),
    0,
  );

  function toggleSeat(seatNumber: string) {
    setSelected((current) => {
      if (current.includes(seatNumber)) return current.filter((s) => s !== seatNumber);
      if (current.length >= MAX_SEATS) {
        toast.info(`You can hold up to ${MAX_SEATS} seats at a time`);
        return current;
      }
      return [...current, seatNumber];
    });
  }

  function confirmSelection() {
    if (selected.length === 0) return;

    holdSeats.mutate(
      { tripId, seatNumbers: selected },
      {
        onSuccess: (hold) => {
          setHold({
            seatHoldId: hold.seatHoldId,
            seatNumbers: hold.seatNumbers,
            expiresAt: hold.expiresAt,
          });
          router.push('/booking/passengers');
        },
        onError: (mutationError) => {
          // 409 means someone else took a seat between render and submit — refetch so the map
          // reflects reality instead of leaving a stale seat looking selectable.
          if (mutationError instanceof ApiError && mutationError.status === 409) {
            toast.error('Those seats just went', {
              description: 'Someone else booked one of them. We have refreshed the map.',
            });
            setSelected([]);
            void refetch();
            return;
          }
          toast.error('Could not hold those seats', {
            description:
              mutationError instanceof Error ? mutationError.message : 'Please try again.',
          });
        },
      },
    );
  }

  return (
    <PageShell
      width="wide"
      backHref={`/trips/${tripId}`}
      backLabel="Trip details"
      title="Choose your seats"
      description={
        trip ? `${trip.operatorName} · ${trip.origin} → ${trip.destination}` : undefined
      }
    >
      <FlowSteps current="Seats" />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
        <div>
          {isLoading ? (
            <Card padding="lg">
              <div className="grid grid-cols-4 gap-2.5 sm:grid-cols-6">
                {Array.from({ length: 24 }).map((_, index) => (
                  <Skeleton key={index} className="aspect-square w-full rounded-sm" />
                ))}
              </div>
            </Card>
          ) : isError ? (
            <ErrorState
              error={error}
              title="We couldn't load the seat map"
              onRetry={() => void refetch()}
            />
          ) : seats.length === 0 ? (
            <EmptyState
              icon={<ArmchairIcon />}
              title="No seat map for this trip"
              description="The operator hasn't published a seat layout. Try another trip on this route."
            />
          ) : availableCount === 0 ? (
            <EmptyState
              icon={<ArmchairIcon />}
              title="This bus is full"
              description="Every seat on this trip is taken. Try a different departure."
            />
          ) : (
            <FadeIn>
              <SeatMap
                seats={seats}
                selected={selected}
                onToggle={toggleSeat}
                maxSelectable={MAX_SEATS}
              />
            </FadeIn>
          )}
        </div>

        {/* Selection summary — hidden on mobile in favour of the sticky bar below, so the
            primary action is always reachable while the user is picking seats. */}
        <FadeIn delay={0.1} className="hidden lg:sticky lg:top-24 lg:block">
          <Card variant="elevated" padding="lg" className="flex flex-col gap-5">
            <div className="flex items-baseline justify-between">
              <h2 className="text-h3">Your selection</h2>
              <span className="text-caption text-content-muted">
                {selected.length}/{MAX_SEATS}
              </span>
            </div>

            <div className="min-h-16">
              <AnimatePresence mode="popLayout">
                {selectedSeats.length === 0 ? (
                  <motion.p
                    key="empty"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="text-body text-content-muted"
                  >
                    Tap a seat on the map to select it.
                  </motion.p>
                ) : (
                  <motion.ul key="list" className="flex flex-col gap-2.5">
                    {selectedSeats.map((seat) => (
                      <motion.li
                        key={seat.seatNumber}
                        layout
                        initial={{ opacity: 0, x: -8 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: 8 }}
                        transition={{ duration: 0.2 }}
                        className="flex items-center justify-between gap-3"
                      >
                        <span className="flex items-center gap-2.5">
                          <Badge tone="accent">{seat.seatNumber}</Badge>
                          <span className="text-caption capitalize text-content-secondary">
                            {seat.seatType.toLowerCase()}
                          </span>
                        </span>
                        <span className="text-caption tabular-nums text-content">
                          {formatMoney(seat.priceAmount, seat.priceCurrency)}
                        </span>
                      </motion.li>
                    ))}
                  </motion.ul>
                )}
              </AnimatePresence>
            </div>

            <Separator />

            <div className="flex items-baseline justify-between">
              <span className="text-body text-content-secondary">Total</span>
              <span className="text-h3 tabular-nums">{formatMoney(total, currency)}</span>
            </div>

            <Button
              size="lg"
              full
              disabled={selected.length === 0}
              loading={holdSeats.isPending}
              loadingText="Holding your seats"
              onClick={confirmSelection}
            >
              Hold {selected.length > 0 ? `${selected.length} ` : ''}
              {selected.length === 1 ? 'seat' : 'seats'}
              <ArrowRight />
            </Button>

            <p className="flex items-start gap-2 text-caption text-content-muted">
              <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              Holding reserves your seats for a limited window while you enter passenger details.
            </p>
          </Card>
        </FadeIn>
      </div>

      {/* Mobile sticky action bar */}
      <AnimatePresence>
        {selected.length > 0 && (
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', stiffness: 380, damping: 34 }}
            className="fixed inset-x-0 bottom-0 z-40 border-t border-line-strong bg-overlay p-4 pb-[max(1rem,env(safe-area-inset-bottom))] shadow-lg lg:hidden"
          >
            <div className="mx-auto flex max-w-2xl items-center gap-4">
              <div className="min-w-0 flex-1">
                <p className="truncate text-caption text-content-secondary">
                  {selected.length} {selected.length === 1 ? 'seat' : 'seats'} ·{' '}
                  {selectedSeats.map((s) => s.seatNumber).join(', ')}
                </p>
                <p className="text-h3 leading-tight tabular-nums">
                  {formatMoney(total, currency)}
                </p>
              </div>
              <Button
                size="lg"
                loading={holdSeats.isPending}
                loadingText="Holding"
                onClick={confirmSelection}
              >
                Continue
                <ArrowRight />
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </PageShell>
  );
}
