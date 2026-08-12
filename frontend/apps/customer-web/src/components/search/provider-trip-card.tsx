'use client';

import Link from 'next/link';
import { ArrowRight, Radio } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { formatDuration, formatMoney, formatTime } from '@/lib/utils/format';
import type { ProviderTripResponse } from '@/lib/api/types';

/**
 * One live provider result.
 *
 * Deliberately a sibling of `TripCard` rather than a variant of it: the two describe different
 * things. An indexed trip is a ranked, paged, first-party read model with ratings and amenities; a
 * provider trip is fetched live, has none of that, and carries a provider's name that must be shown
 * as itself. Folding them into one component would mean inventing values for whichever half the
 * other lacks.
 *
 * A trip with no `catalogTripId` renders as a card rather than a link. It is a real departure the
 * provider is advertising, but catalog sync has not imported it, so there is nothing to select —
 * and saying so here is better than letting the traveller click through to a seat map that cannot
 * exist.
 */
export function ProviderTripCard({ trip }: { trip: ProviderTripResponse }) {
  const durationMinutes = Math.round(
    (new Date(trip.arrivalTime).getTime() - new Date(trip.departureTime).getTime()) / 60000,
  );
  const bookable = trip.catalogTripId !== null;
  const scarce = trip.seatsAvailable > 0 && trip.seatsAvailable <= 5;

  const body = (
    <div className="flex flex-col gap-5 sm:flex-row sm:items-stretch sm:gap-6">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-3 sm:gap-4">
          <div className="text-left">
            <p className="text-h2 leading-none tabular-nums">{formatTime(trip.departureTime)}</p>
            <p className="mt-1.5 truncate text-caption text-content-secondary">{trip.origin}</p>
          </div>

          <div className="flex flex-1 flex-col items-center gap-1.5 px-1">
            <span className="text-micro uppercase text-content-muted">
              {formatDuration(durationMinutes)}
            </span>
            <div className="relative h-px w-full bg-line-strong">
              <span className="absolute -top-[3px] left-0 size-[7px] rounded-full border border-line-strong bg-surface" />
              <span className="absolute -top-[3px] right-0 size-[7px] rounded-full bg-accent" />
            </div>
          </div>

          <div className="text-right">
            <p className="text-h2 leading-none tabular-nums">{formatTime(trip.arrivalTime)}</p>
            <p className="mt-1.5 truncate text-caption text-content-secondary">{trip.destination}</p>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <span className="truncate font-medium text-content">{trip.operatorName}</span>
          {trip.serviceClass && (
            <>
              <span className="text-content-muted" aria-hidden>
                ·
              </span>
              <span className="truncate text-caption text-content-secondary">
                {trip.serviceClass}
              </span>
            </>
          )}
          {/* The provider is named as itself. Presenting one provider's inventory under another's
              name would misrepresent who the traveller is actually buying from. */}
          <Badge tone="neutral" className="shrink-0">
            {trip.providerCode}
          </Badge>
        </div>
      </div>

      <div className="flex items-center justify-between gap-4 border-t border-line pt-4 sm:w-44 sm:flex-col sm:items-end sm:justify-center sm:border-l sm:border-t-0 sm:pl-6 sm:pt-0">
        <div className="sm:text-right">
          <p className="text-h2 leading-none tabular-nums text-content">
            {formatMoney(trip.fareAmount, trip.fareCurrency)}
          </p>
          <p className="mt-1.5 text-micro uppercase text-content-muted">per seat</p>
        </div>

        <div className="flex items-center gap-2 sm:mt-4">
          {!bookable ? (
            <Badge tone="neutral">Not yet bookable</Badge>
          ) : scarce ? (
            <Badge tone="warning">{trip.seatsAvailable} left</Badge>
          ) : (
            <Badge tone="success">{trip.seatsAvailable} seats</Badge>
          )}
          {bookable && (
            <ArrowRight
              className="size-4 shrink-0 text-content-muted transition-transform duration-300 group-hover:translate-x-1 group-hover:text-accent"
              aria-hidden
            />
          )}
        </div>
      </div>
    </div>
  );

  return (
    <Card variant="solid" interactive={bookable} padding="none" className="group overflow-hidden">
      {bookable ? (
        <Link
          href={`/trips/${trip.catalogTripId}`}
          className="block p-5 focus:outline-none sm:p-6"
          aria-label={`${trip.operatorName} via ${trip.providerCode}, departs ${formatTime(trip.departureTime)}, ${formatMoney(trip.fareAmount, trip.fareCurrency)}`}
        >
          {body}
        </Link>
      ) : (
        <div className="p-5 sm:p-6">{body}</div>
      )}
    </Card>
  );
}

/**
 * Section heading for the live provider results, plus the honest account of who did not answer.
 *
 * A failed provider is reported rather than hidden: an unreachable provider and a provider with no
 * buses on the route produce the same empty list, and letting the traveller read one as the other
 * tells them a route is sold out when nobody actually asked.
 */
export function ProviderResultsHeader({ complete }: { complete: boolean }) {
  return (
    <div className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1.5">
      <h2 className="flex items-center gap-2 text-caption font-medium uppercase tracking-wide text-content-secondary">
        <Radio className="size-3.5 text-accent" aria-hidden />
        Provider buses
      </h2>
      {!complete && (
        <span className="text-caption text-warning">
          Some providers are temporarily unavailable — this list may be incomplete.
        </span>
      )}
    </div>
  );
}
