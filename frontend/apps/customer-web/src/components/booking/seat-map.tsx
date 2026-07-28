'use client';

import * as React from 'react';
import { motion } from 'framer-motion';
import { Accessibility, Check, CircleSlash, LifeBuoy } from 'lucide-react';
import { Tooltip } from '@/components/ui/misc';
import { formatMoney } from '@/lib/utils/format';
import { usePrefersReducedMotion } from '@/lib/hooks/use-reduced-motion';
import { cn } from '@/lib/utils/cn';
import type { SeatViewResponse } from '@/lib/api/types';

/**
 * Seat deck map, drawn as a bus interior rather than an abstract grid.
 *
 * The backend returns a flat seat list carrying `deck` and an optional `position`; it does not
 * describe geometry. So seats are grouped by deck, ordered by `position` (falling back to a
 * natural sort of `seatNumber`), then laid out 2 + aisle + 2 — the near-universal coach
 * arrangement. The aisle is a real column, not a margin hack, so rows line up on every width.
 *
 * Only `AVAILABLE` seats are selectable. Every other status renders as a visibly *inert* seat
 * rather than disappearing, so the bus still reads as a bus and the user can see how full it is.
 */

export const SELECTABLE_STATUS = 'AVAILABLE';

const SEATS_PER_ROW = 4;
const AISLE_AFTER = 2;

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
      return 'Held by the operator';
    case 'UNAVAILABLE':
      return 'Unavailable';
    default:
      return 'Status unknown';
  }
}

function Seat({
  seat,
  selected,
  atLimit,
  onToggle,
}: {
  seat: SeatViewResponse;
  selected: boolean;
  atLimit: boolean;
  onToggle: () => void;
}) {
  const reduced = usePrefersReducedMotion();
  const selectable = seat.status === SELECTABLE_STATUS;
  const blocked = !selectable;
  const disabled = blocked || (atLimit && !selected);

  return (
    <Tooltip
      side="top"
      content={
        <span className="flex flex-col gap-0.5">
          <span className="font-semibold text-content">Seat {seat.seatNumber}</span>
          <span className="capitalize text-content-secondary">{seat.seatType.toLowerCase()}</span>
          <span className="text-content-secondary">{statusLabel(seat.status)}</span>
          {selectable && (
            <span className="mt-0.5 font-medium text-content">
              {formatMoney(seat.priceAmount, seat.priceCurrency)}
            </span>
          )}
        </span>
      }
    >
      <motion.button
        type="button"
        onClick={onToggle}
        disabled={disabled}
        aria-pressed={selected}
        aria-label={`Seat ${seat.seatNumber}, ${statusLabel(seat.status)}${
          selectable ? `, ${formatMoney(seat.priceAmount, seat.priceCurrency)}` : ''
        }`}
        whileHover={disabled || reduced ? undefined : { y: -2 }}
        whileTap={disabled || reduced ? undefined : { scale: 0.94 }}
        transition={{ type: 'spring', stiffness: 420, damping: 26 }}
        className={cn(
          'group relative flex aspect-[5/6] w-full flex-col items-center justify-center gap-0.5',
          // The seat silhouette: rounded seat-back with a squarer base.
          'rounded-t-lg rounded-b-md border-2 transition-colors duration-200',
          'focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent-ring/40',

          selected && 'border-accent bg-accent text-white shadow-glow',

          !selected &&
            selectable &&
            'border-line-strong bg-elevated text-content-secondary hover:border-accent hover:bg-accent-soft hover:text-content',

          blocked &&
            'cursor-not-allowed border-transparent bg-white/[0.035] text-content-muted/70',

          atLimit && !selected && selectable && 'cursor-not-allowed opacity-40',
        )}
      >
        {/* Armrest nubs — two hairlines that make the shape read as a seat, not a button. */}
        {!blocked && (
          <>
            <span
              className={cn(
                'absolute left-0 top-1/3 h-1/3 w-[3px] rounded-r-full transition-colors',
                selected ? 'bg-white/45' : 'bg-line-strong group-hover:bg-accent/50',
              )}
              aria-hidden
            />
            <span
              className={cn(
                'absolute right-0 top-1/3 h-1/3 w-[3px] rounded-l-full transition-colors',
                selected ? 'bg-white/45' : 'bg-line-strong group-hover:bg-accent/50',
              )}
              aria-hidden
            />
          </>
        )}

        {blocked ? (
          <CircleSlash className="size-3.5 opacity-60" aria-hidden />
        ) : selected ? (
          <motion.span
            initial={reduced ? false : { scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 500, damping: 22 }}
          >
            <Check className="size-4" aria-hidden strokeWidth={3} />
          </motion.span>
        ) : null}

        <span
          className={cn(
            'text-[0.6875rem] font-semibold tabular-nums leading-none',
            selected && 'sr-only',
          )}
        >
          {seat.seatNumber}
        </span>

        {seat.wheelchairAccessible && (
          <Accessibility
            className={cn(
              'absolute right-1 top-1 size-3',
              selected ? 'text-white/80' : 'text-info',
            )}
            aria-hidden
          />
        )}
      </motion.button>
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
    <div className="flex flex-col gap-6">
      {decks.map(([deck, deckSeats]) => {
        // Chunk into rows so the aisle can be a real grid column.
        const rows: SeatViewResponse[][] = [];
        for (let i = 0; i < deckSeats.length; i += SEATS_PER_ROW) {
          rows.push(deckSeats.slice(i, i + SEATS_PER_ROW));
        }
        const availableOnDeck = deckSeats.filter((s) => s.status === SELECTABLE_STATUS).length;

        return (
          <section key={deck} aria-label={`${deck.toLowerCase()} deck`}>
            <div className="mb-3 flex items-baseline justify-between gap-3">
              <h3 className="text-micro uppercase text-content-muted">
                {decks.length > 1 ? `${deck} deck` : 'Seat map'}
              </h3>
              <p className="text-caption text-content-secondary">
                <span className="font-semibold text-content">{availableOnDeck}</span> of{' '}
                {deckSeats.length} free
              </p>
            </div>

            {/* Bus shell */}
            <div className="overflow-hidden rounded-2xl border border-line bg-surface">
              {/* Driver row orients the map — without it a seat grid has no front. */}
              <div className="flex items-center justify-between gap-3 border-b border-line bg-white/[0.02] px-4 py-2.5">
                <span className="text-micro uppercase text-content-muted">Front</span>
                <span className="flex items-center gap-1.5 text-content-muted">
                  <LifeBuoy className="size-4" aria-hidden />
                </span>
              </div>

              <div className="flex flex-col gap-2 p-4 sm:gap-2.5 sm:p-5">
                {rows.map((row, rowIndex) => (
                  <div
                    key={`${deck}-row-${rowIndex}`}
                    className="grid items-center gap-2 sm:gap-2.5"
                    style={{
                      gridTemplateColumns: `repeat(${AISLE_AFTER}, minmax(0,1fr)) 1.25rem repeat(${
                        SEATS_PER_ROW - AISLE_AFTER
                      }, minmax(0,1fr))`,
                    }}
                  >
                    {row.slice(0, AISLE_AFTER).map((seat) => (
                      <Seat
                        key={seat.seatNumber}
                        seat={seat}
                        selected={selected.includes(seat.seatNumber)}
                        atLimit={atLimit}
                        onToggle={() => onToggle(seat.seatNumber)}
                      />
                    ))}

                    {/* Aisle: row number, centred */}
                    <span
                      className="text-center text-[0.625rem] tabular-nums text-content-muted/60"
                      aria-hidden
                    >
                      {rowIndex + 1}
                    </span>

                    {row.slice(AISLE_AFTER).map((seat) => (
                      <Seat
                        key={seat.seatNumber}
                        seat={seat}
                        selected={selected.includes(seat.seatNumber)}
                        atLimit={atLimit}
                        onToggle={() => onToggle(seat.seatNumber)}
                      />
                    ))}
                  </div>
                ))}
              </div>
            </div>
          </section>
        );
      })}

      <SeatLegend />
    </div>
  );
}

export function SeatLegend() {
  const items = [
    { className: 'border-line-strong bg-elevated', label: 'Available' },
    { className: 'border-accent bg-accent', label: 'Selected' },
    { className: 'border-transparent bg-white/[0.035]', label: 'Taken' },
  ];

  return (
    <ul className="flex flex-wrap items-center gap-x-5 gap-y-2.5">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-2 text-caption text-content-secondary">
          <span
            className={cn('size-4 rounded-t-md rounded-b-sm border-2', item.className)}
            aria-hidden
          />
          {item.label}
        </li>
      ))}
      <li className="flex items-center gap-2 text-caption text-content-secondary">
        <Accessibility className="size-4 text-info" aria-hidden />
        Accessible
      </li>
    </ul>
  );
}
