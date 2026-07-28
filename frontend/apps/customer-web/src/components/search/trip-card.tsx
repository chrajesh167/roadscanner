'use client';

import Link from 'next/link';
import { ArrowRight, Star } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/feedback';
import { AmenityList } from './amenity-list';
import { formatDuration, formatMoney, formatTime } from '@/lib/utils/format';
import type { TripResponse } from '@/lib/api/types';

/** One result row. The whole card is a link; nothing inside it is separately focusable. */
export function TripCard({ trip }: { trip: TripResponse }) {
  const seatsKnown = trip.availabilityKnown && trip.availableSeats !== null;
  const scarce = seatsKnown && trip.availableSeats! <= 5;

  return (
    <Card
      variant="solid"
      interactive={trip.bookable}
      padding="none"
      className="group overflow-hidden"
    >
      <Link
        href={`/trips/${trip.tripId}`}
        className="flex flex-col gap-5 p-5 focus:outline-none sm:p-6"
        aria-label={`${trip.operatorName}, departs ${formatTime(trip.departureTime)}, ${formatMoney(trip.fareAmount, trip.fareCurrency)}`}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="truncate text-[1rem] font-medium text-content">{trip.operatorName}</p>
            <p className="mt-0.5 text-caption text-content-muted">{trip.busTypeCategory}</p>
          </div>

          {trip.ratingReviewCount > 0 && (
            <span className="flex shrink-0 items-center gap-1.5 rounded-full border border-line-strong bg-white/[0.04] px-2.5 py-1 text-caption">
              <Star className="size-3 fill-warning text-warning" />
              <span className="text-content">{trip.ratingAverage.toFixed(1)}</span>
              <span className="text-content-muted">({trip.ratingReviewCount})</span>
            </span>
          )}
        </div>

        {/* Departure → arrival, with the duration riding the connecting rule. */}
        <div className="flex items-center gap-4">
          <div className="text-left">
            <p className="text-h3 tabular-nums">{formatTime(trip.departureTime)}</p>
            <p className="mt-0.5 truncate text-caption text-content-muted">{trip.origin}</p>
          </div>

          <div className="flex flex-1 flex-col items-center gap-1.5">
            <span className="text-micro uppercase text-content-muted">
              {formatDuration(trip.durationMinutes)}
            </span>
            <div className="relative h-px w-full bg-line-strong">
              <span className="absolute -top-[3px] left-0 size-[7px] rounded-full border border-line-strong bg-surface" />
              <span className="absolute -top-[3px] right-0 size-[7px] rounded-full bg-accent" />
            </div>
          </div>

          <div className="text-right">
            <p className="text-h3 tabular-nums">{formatTime(trip.arrivalTime)}</p>
            <p className="mt-0.5 truncate text-caption text-content-muted">{trip.destination}</p>
          </div>
        </div>

        <AmenityList amenities={trip.amenities} max={4} />

        <div className="flex items-end justify-between gap-4 border-t border-line pt-4">
          <div className="flex flex-wrap items-center gap-2">
            {!trip.bookable ? (
              <Badge tone="danger">Not bookable</Badge>
            ) : scarce ? (
              <Badge tone="warning">{trip.availableSeats} seats left</Badge>
            ) : seatsKnown ? (
              <Badge tone="success">{trip.availableSeats} seats</Badge>
            ) : (
              /* availabilityKnown=false means the live overlay was unavailable, which is not the
                 same as "sold out" — say so rather than implying a count. */
              <Badge tone="neutral">Availability at checkout</Badge>
            )}
          </div>

          <div className="flex items-center gap-3">
            <p className="text-h3 tabular-nums">
              {formatMoney(trip.fareAmount, trip.fareCurrency)}
            </p>
            <ArrowRight className="size-4 text-content-muted transition-transform duration-300 group-hover:translate-x-1 group-hover:text-accent" />
          </div>
        </div>
      </Link>
    </Card>
  );
}

export function TripCardSkeleton() {
  return (
    <Card padding="md" className="flex flex-col gap-5">
      <div className="flex justify-between">
        <div className="flex flex-col gap-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-3 w-24" />
        </div>
        <Skeleton className="h-6 w-16 rounded-full" />
      </div>
      <div className="flex items-center gap-4">
        <Skeleton className="h-7 w-14" />
        <Skeleton className="h-px flex-1" />
        <Skeleton className="h-7 w-14" />
      </div>
      <div className="flex justify-between border-t border-line pt-4">
        <Skeleton className="h-5 w-24 rounded-full" />
        <Skeleton className="h-6 w-20" />
      </div>
    </Card>
  );
}
