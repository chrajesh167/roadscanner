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
        className="block p-5 focus:outline-none sm:p-6"
        aria-label={`${trip.operatorName}, departs ${formatTime(trip.departureTime)}, ${formatMoney(trip.fareAmount, trip.fareCurrency)}`}
      >
        {/*
         * Hierarchy, strongest first: journey times → fare → operator → amenities.
         * Previously the operator name led and the fare was buried in the footer, which is the
         * wrong order — people scan a results list by time and price, then decide on the brand.
         * On >=sm the fare gets its own right-hand column so it aligns down the whole list.
         */}
        <div className="flex flex-col gap-5 sm:flex-row sm:items-stretch sm:gap-6">
          <div className="min-w-0 flex-1">
            {/* Times */}
            <div className="flex items-center gap-3 sm:gap-4">
              <div className="text-left">
                <p className="text-h2 leading-none tabular-nums">
                  {formatTime(trip.departureTime)}
                </p>
                <p className="mt-1.5 truncate text-caption text-content-secondary">{trip.origin}</p>
              </div>

              <div className="flex flex-1 flex-col items-center gap-1.5 px-1">
                <span className="text-micro uppercase text-content-muted">
                  {formatDuration(trip.durationMinutes)}
                </span>
                <div className="relative h-px w-full bg-line-strong">
                  <span className="absolute -top-[3px] left-0 size-[7px] rounded-full border border-line-strong bg-surface" />
                  <span className="absolute -top-[3px] right-0 size-[7px] rounded-full bg-accent" />
                </div>
              </div>

              <div className="text-right">
                <p className="text-h2 leading-none tabular-nums">
                  {formatTime(trip.arrivalTime)}
                </p>
                <p className="mt-1.5 truncate text-caption text-content-secondary">
                  {trip.destination}
                </p>
              </div>
            </div>

            {/* Operator + rating, demoted to a supporting line */}
            <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <span className="truncate font-medium text-content">{trip.operatorName}</span>
              <span className="text-content-muted" aria-hidden>
                ·
              </span>
              <span className="truncate text-caption text-content-secondary">
                {trip.busTypeCategory}
              </span>
              {trip.ratingReviewCount > 0 && (
                <span className="flex shrink-0 items-center gap-1 text-caption">
                  <Star className="size-3.5 fill-warning text-warning" aria-hidden />
                  <span className="font-medium text-content">{trip.ratingAverage.toFixed(1)}</span>
                  <span className="text-content-muted">({trip.ratingReviewCount})</span>
                </span>
              )}
            </div>

            <AmenityList amenities={trip.amenities} max={4} className="mt-3.5" />
          </div>

          {/* Fare column */}
          <div className="flex items-center justify-between gap-4 border-t border-line pt-4 sm:w-44 sm:flex-col sm:items-end sm:justify-center sm:border-l sm:border-t-0 sm:pl-6 sm:pt-0">
            <div className="sm:text-right">
              <p className="text-h2 leading-none tabular-nums text-content">
                {formatMoney(trip.fareAmount, trip.fareCurrency)}
              </p>
              <p className="mt-1.5 text-micro uppercase text-content-muted">per seat</p>
            </div>

            <div className="flex items-center gap-2 sm:mt-4">
              {!trip.bookable ? (
                <Badge tone="danger">Not bookable</Badge>
              ) : scarce ? (
                <Badge tone="warning">{trip.availableSeats} left</Badge>
              ) : seatsKnown ? (
                <Badge tone="success">{trip.availableSeats} seats</Badge>
              ) : (
                /* availabilityKnown=false means the live overlay was unavailable, which is not the
                   same as "sold out" — say so rather than implying a count. */
                <Badge tone="neutral">Seats at checkout</Badge>
              )}
              <ArrowRight
                className="size-4 shrink-0 text-content-muted transition-transform duration-300 group-hover:translate-x-1 group-hover:text-accent"
                aria-hidden
              />
            </div>
          </div>
        </div>
      </Link>
    </Card>
  );
}

/** Mirrors the real card's two-column shape so nothing shifts when data lands. */
export function TripCardSkeleton() {
  return (
    <Card padding="md">
      <div className="flex flex-col gap-5 sm:flex-row sm:gap-6">
        <div className="flex-1">
          <div className="flex items-center gap-4">
            <Skeleton className="h-8 w-16" />
            <Skeleton className="h-px flex-1" />
            <Skeleton className="h-8 w-16" />
          </div>
          <Skeleton className="mt-4 h-4 w-52" />
          <Skeleton className="mt-3.5 h-3.5 w-64" />
        </div>
        <div className="flex items-center justify-between gap-4 border-t border-line pt-4 sm:w-44 sm:flex-col sm:items-end sm:justify-center sm:border-l sm:border-t-0 sm:pl-6 sm:pt-0">
          <Skeleton className="h-8 w-24" />
          <Skeleton className="h-5 w-20 rounded-full sm:mt-4" />
        </div>
      </div>
    </Card>
  );
}
