'use client';

import { useRouter } from 'next/navigation';
import { ArrowRight, Bus, CalendarClock, MapPin, Star, Timer } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState, Skeleton, SkeletonText } from '@/components/ui/feedback';
import { Separator } from '@/components/ui/misc';
import { FadeIn } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { AmenityList } from '@/components/search/amenity-list';
import { useTripDetail } from '@/lib/hooks/use-search';
import { useSession } from '@/lib/hooks/use-auth';
import { useBookingFlowStore } from '@/lib/store/booking-flow-store';
import {
  formatDateTime,
  formatDuration,
  formatMoney,
  formatTime,
} from '@/lib/utils/format';

export function TripDetailView({ tripId }: { tripId: string }) {
  const router = useRouter();
  const { data: trip, isLoading, isError, error, refetch } = useTripDetail(tripId);
  const { isAuthenticated } = useSession();
  const startFlow = useBookingFlowStore((state) => state.startFlow);

  function continueToSeats() {
    if (!trip) return;
    // The trip is cached into the flow so later screens can price the booking without refetching.
    startFlow(trip);
    const target = `/trips/${tripId}/seats`;
    router.push(isAuthenticated ? target : `/login?next=${encodeURIComponent(target)}`);
  }

  if (isLoading) {
    return (
      <PageShell backHref="/search">
        <Card padding="lg" className="flex flex-col gap-6">
          <Skeleton className="h-7 w-56" />
          <SkeletonText lines={2} />
          <Skeleton className="h-24 w-full rounded-md" />
        </Card>
      </PageShell>
    );
  }

  if (isError || !trip) {
    return (
      <PageShell backHref="/search">
        <ErrorState
          error={error}
          title="We couldn't load this trip"
          onRetry={() => void refetch()}
        />
      </PageShell>
    );
  }

  const seatsKnown = trip.availabilityKnown && trip.availableSeats !== null;

  return (
    <PageShell
      backHref="/search"
      backLabel="Back to search"
      title={trip.operatorName}
      description={`${trip.origin} → ${trip.destination}`}
      actions={
        trip.ratingReviewCount > 0 ? (
          <span className="flex items-center gap-1.5 rounded-full border border-line-strong bg-white/[0.04] px-3 py-1.5 text-caption">
            <Star className="size-3.5 fill-warning text-warning" />
            <span className="text-content">{trip.ratingAverage.toFixed(1)}</span>
            <span className="text-content-muted">({trip.ratingReviewCount} reviews)</span>
          </span>
        ) : undefined
      }
    >
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
        <div className="flex flex-col gap-6">
          {/* Journey timeline */}
          <FadeIn>
            <Card padding="lg">
              <div className="flex gap-5">
                <div className="flex flex-col items-center pt-1.5">
                  <span className="size-2.5 rounded-full bg-accent" />
                  <span className="my-1 w-px flex-1 bg-line-strong" />
                  <span className="size-2.5 rounded-full border border-line-strong bg-surface" />
                </div>

                <div className="flex flex-1 flex-col gap-8">
                  <div>
                    <p className="text-h2 tabular-nums">{formatTime(trip.departureTime)}</p>
                    <p className="mt-1 flex items-center gap-1.5 text-body text-content">
                      <MapPin className="size-3.5 text-content-muted" aria-hidden />
                      {trip.origin}
                    </p>
                    <p className="mt-0.5 text-caption text-content-muted">
                      {formatDateTime(trip.departureTime)}
                    </p>
                  </div>

                  <div className="flex items-center gap-2 text-caption text-content-secondary">
                    <Timer className="size-3.5 text-content-muted" aria-hidden />
                    {formatDuration(trip.durationMinutes)} journey
                  </div>

                  <div>
                    <p className="text-h2 tabular-nums">{formatTime(trip.arrivalTime)}</p>
                    <p className="mt-1 flex items-center gap-1.5 text-body text-content">
                      <MapPin className="size-3.5 text-content-muted" aria-hidden />
                      {trip.destination}
                    </p>
                    <p className="mt-0.5 text-caption text-content-muted">
                      {formatDateTime(trip.arrivalTime)}
                    </p>
                  </div>
                </div>
              </div>
            </Card>
          </FadeIn>

          {/* Bus & amenities */}
          <FadeIn delay={0.08}>
            <Card padding="lg">
              <h2 className="flex items-center gap-2 text-h3">
                <Bus className="size-4 text-content-muted" aria-hidden />
                On this bus
              </h2>
              <p className="mt-2 text-body text-content-secondary">{trip.busTypeCategory}</p>

              {trip.amenities.length > 0 && (
                <>
                  <Separator className="my-5" />
                  <AmenityList amenities={trip.amenities} />
                </>
              )}
            </Card>
          </FadeIn>
        </div>

        {/* Booking panel */}
        <FadeIn delay={0.14} className="lg:sticky lg:top-24">
          <Card variant="elevated" padding="lg" className="flex flex-col gap-5">
            <div>
              <p className="text-micro uppercase text-content-muted">Fare per seat</p>
              <p className="mt-1.5 text-h1 tabular-nums">
                {formatMoney(trip.fareAmount, trip.fareCurrency)}
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              {!trip.bookable ? (
                <Badge tone="danger" size="md">
                  Not bookable
                </Badge>
              ) : seatsKnown ? (
                <Badge tone={trip.availableSeats! <= 5 ? 'warning' : 'success'} size="md">
                  {trip.availableSeats} seats available
                </Badge>
              ) : (
                <Badge tone="neutral" size="md">
                  Availability confirmed at seat selection
                </Badge>
              )}
            </div>

            <Separator />

            <Button
              size="lg"
              full
              disabled={!trip.bookable}
              onClick={continueToSeats}
            >
              {trip.bookable ? 'Choose seats' : 'Unavailable'}
              {trip.bookable && <ArrowRight />}
            </Button>

            {!isAuthenticated && trip.bookable && (
              <p className="flex items-start gap-2 text-caption text-content-muted">
                <CalendarClock className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                You&apos;ll be asked to sign in before seats can be held.
              </p>
            )}
          </Card>
        </FadeIn>
      </div>
    </PageShell>
  );
}
