'use client';

import * as React from 'react';
import { Accessibility, Armchair } from 'lucide-react';
import { Pressable } from '@/components/ui/motion';
import { Tooltip } from '@/components/ui/misc';
import { formatMoney } from '@/lib/utils/format';
import { cn } from '@/lib/utils/cn';
import type { SeatViewResponse } from '@/lib/api/types';

/**
 * Seat deck map.
 *
 * The backend returns a flat seat list carrying `deck` and an optional `position`; it does not
 * describe a grid. So seats are grouped by deck and ordered by `position` (falling back to a
 * natural sort of `seatNumber`), then flowed into a responsive grid with an aisle gap. That
 * renders any layout the catalogue produces without assuming a fixed column count.
 *
 * Only `AVAILABLE` seats are selectable — every other status (`BOOKED`, `BLOCKED`, `UNAVAILABLE`,
 * or `UNKNOWN` when the live overlay is missing) is rendered disabled rather than hidden, so the
 * bus still reads as a bus.
 */

export const SELECTABLE_STATUS = 'AVAILABLE';

function naturalCompare(a: SeatViewResponse, b: SeatViewResponse): number {
  if (a.position !== null && b.position !== null) return a.position - b.position;
  if (a.position !== null) return -1;
  if (b.position !== null) return 1;
  return a.seatNumber.localeCompare(b.seatNumber, undefined, { numeric: true });
}

function statusLabel(status: string): string {
  switch (status) {
    case 'AVAILABLE':
      return 'Available';
    case 'BOOKED':
      return 'Already booked';
    case 'BLOCKED':
      return 'Blocked by the operator';
    case 'UNAVAILABLE':
      return 'Unavailable';
    default:
      return 'Status unknown';
  }
}

function Seat({
  seat,
  selected,
  disabled,
  onToggle,
}: {
  seat: SeatViewResponse;
  selected: boolean;
  disabled: boolean;
  onToggle: () => void;
}) {
  const selectable = seat.status === SELECTABLE_STATUS;
  const blocked = !selectable;

  return (
    <Tooltip
      content={
        <span className="flex flex-col gap-0.5">
          <span className="font-medium text-content">Seat {seat.seatNumber}</span>
          <span className="text-content-secondary">{seat.seatType.toLowerCase()}</span>
          <span className="text-content-secondary">{statusLabel(seat.status)}</span>
          {selectable && (
            <span className="text-content">{formatMoney(seat.priceAmount, seat.priceCurrency)}</span>
          )}
        </span>
      }
    >
      <Pressable
        onClick={onToggle}
        disabled={blocked || disabled}
        ariaPressed={selected}
        ariaLabel={`Seat ${seat.seatNumber}, ${statusLabel(seat.status)}${
          selectable ? `, ${formatMoney(seat.priceAmount, seat.priceCurrency)}` : ''
        }`}
        className={cn(
          'group relative grid aspect-square w-full place-items-center rounded-sm border',
          'transition-colors duration-200',
          selected && 'border-accent bg-accent text-white shadow-glow',
          !selected && selectable && 'border-line-strong bg-elevated text-content-secondary hover:border-accent/50 hover:text-content',
          blocked && 'border-line bg-white/[0.02] text-content-muted opacity-45',
          disabled && !selected && !blocked && 'opacity-50',
        )}
      >
        <Armchair className="size-4" aria-hidden />
        <span className="mt-0.5 text-[0.625rem] font-medium tabular-nums">{seat.seatNumber}</span>
        {seat.wheelchairAccessible && (
          <Accessibility className="absolute right-0.5 top-0.5 size-2.5 text-info" aria-hidden />
        )}
      </Pressable>
    </Tooltip>
  );
}

export function SeatMap({
  seats,
  selected,
  onToggle,
  maxSelectable = 6,
}: {
  seats: SeatViewResponse[];
  selected: string[];
  onToggle: (seatNumber: string) => void;
  maxSelectable?: number;
}) {
  const decks = React.useMemo(() => {
    const grouped = new Map<string, SeatViewResponse[]>();
    for (const seat of seats) {
      const deck = seat.deck || 'MAIN';
      const bucket = grouped.get(deck);
      if (bucket) bucket.push(seat);
      else grouped.set(deck, [seat]);
    }
    for (const bucket of grouped.values()) bucket.sort(naturalCompare);
    return [...grouped.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [seats]);

  const atLimit = selected.length >= maxSelectable;

  return (
    <div className="flex flex-col gap-8">
      {decks.map(([deck, deckSeats]) => (
        <section key={deck} aria-label={`${deck.toLowerCase()} deck`}>
          {decks.length > 1 && (
            <p className="mb-3 text-micro uppercase text-content-muted">{deck} deck</p>
          )}

          <div className="rounded-lg border border-line bg-surface p-4 sm:p-5">
            {/* Driver marker orients the map — without it a seat grid is just a grid. */}
            <div className="mb-4 flex items-center justify-end gap-2 border-b border-line pb-3">
              <span className="text-micro uppercase text-content-muted">Front</span>
              <span className="size-2 rounded-full bg-content-muted" aria-hidden />
            </div>

            <div className="grid grid-cols-4 gap-2 sm:grid-cols-6 sm:gap-2.5">
              {deckSeats.map((seat, index) => (
                <React.Fragment key={`${deck}-${seat.seatNumber}`}>
                  <Seat
                    seat={seat}
                    selected={selected.includes(seat.seatNumber)}
                    disabled={atLimit && !selected.includes(seat.seatNumber)}
                    onToggle={() => onToggle(seat.seatNumber)}
                  />
                  {/* Aisle: a blank cell after every second seat in the row. */}
                  {index % 4 === 1 && <div className="hidden sm:block" aria-hidden />}
                </React.Fragment>
              ))}
            </div>
          </div>
        </section>
      ))}

      <SeatLegend />
    </div>
  );
}

export function SeatLegend() {
  const items = [
    { className: 'border-line-strong bg-elevated', label: 'Available' },
    { className: 'border-accent bg-accent', label: 'Selected' },
    { className: 'border-line bg-white/[0.02] opacity-45', label: 'Unavailable' },
  ];

  return (
    <ul className="flex flex-wrap items-center gap-x-5 gap-y-2">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-2 text-caption text-content-secondary">
          <span className={cn('size-3.5 rounded-xs border', item.className)} aria-hidden />
          {item.label}
        </li>
      ))}
      <li className="flex items-center gap-2 text-caption text-content-secondary">
        <Accessibility className="size-3.5 text-info" aria-hidden />
        Wheelchair accessible
      </li>
    </ul>
  );
}
