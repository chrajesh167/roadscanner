'use client';

import { SlidersHorizontal, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Field, Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { SortOption } from '@/lib/api/types';

export interface FilterValues {
  minFare?: string;
  maxFare?: string;
  busType?: string;
  minRating?: string;
  sort?: SortOption | '';
}

const SORT_LABELS: Record<SortOption, string> = {
  PRICE_ASC: 'Price · low to high',
  PRICE_DESC: 'Price · high to low',
  DEPARTURE_TIME_ASC: 'Departure · earliest',
  DURATION_ASC: 'Duration · shortest',
};

/**
 * Filters map one-to-one onto the query params `GET /api/v1/search/trips` accepts. Nothing is
 * filtered client-side — every refinement is a new server query, so paging stays correct.
 */
export function ResultFilters({
  values,
  onChange,
  onReset,
  resultCount,
}: {
  values: FilterValues;
  onChange: (next: FilterValues) => void;
  onReset: () => void;
  resultCount?: number;
}) {
  const active =
    Boolean(values.minFare) ||
    Boolean(values.maxFare) ||
    Boolean(values.busType) ||
    Boolean(values.minRating) ||
    Boolean(values.sort);

  function update(patch: Partial<FilterValues>) {
    onChange({ ...values, ...patch });
  }

  return (
    <Card variant="solid" padding="md" className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-[0.9375rem] font-medium text-content">
          <SlidersHorizontal className="size-4 text-content-muted" aria-hidden />
          Refine
        </p>
        {active && (
          <Button variant="ghost" size="sm" onClick={onReset}>
            <X />
            Clear
          </Button>
        )}
      </div>

      <Field label="Sort by" htmlFor="sort">
        <Select
          value={values.sort || 'RELEVANCE'}
          onValueChange={(value) =>
            update({ sort: value === 'RELEVANCE' ? '' : (value as SortOption) })
          }
        >
          <SelectTrigger id="sort" aria-label="Sort results">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="RELEVANCE">Recommended</SelectItem>
            {(Object.keys(SORT_LABELS) as SortOption[]).map((option) => (
              <SelectItem key={option} value={option}>
                {SORT_LABELS[option]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>

      <Field label="Fare range" htmlFor="minFare">
        <div className="flex items-center gap-2">
          <Input
            id="minFare"
            type="number"
            min={0}
            inputMode="numeric"
            placeholder="Min"
            value={values.minFare ?? ''}
            onChange={(event) => update({ minFare: event.target.value })}
            aria-label="Minimum fare"
          />
          <span className="text-content-muted" aria-hidden>
            –
          </span>
          <Input
            type="number"
            min={0}
            inputMode="numeric"
            placeholder="Max"
            value={values.maxFare ?? ''}
            onChange={(event) => update({ maxFare: event.target.value })}
            aria-label="Maximum fare"
          />
        </div>
      </Field>

      <Field label="Bus type" htmlFor="busType" hint="Matches the operator's category, e.g. Sleeper">
        <Input
          id="busType"
          placeholder="Any"
          value={values.busType ?? ''}
          onChange={(event) => update({ busType: event.target.value })}
        />
      </Field>

      <Field label="Minimum rating" htmlFor="minRating">
        <Select
          value={values.minRating || 'ANY'}
          onValueChange={(value) => update({ minRating: value === 'ANY' ? '' : value })}
        >
          <SelectTrigger id="minRating" aria-label="Minimum rating">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ANY">Any rating</SelectItem>
            <SelectItem value="3">3.0 and above</SelectItem>
            <SelectItem value="4">4.0 and above</SelectItem>
            <SelectItem value="4.5">4.5 and above</SelectItem>
          </SelectContent>
        </Select>
      </Field>

      {resultCount !== undefined && (
        <p className="border-t border-line pt-4 text-caption text-content-muted">
          {resultCount} {resultCount === 1 ? 'trip' : 'trips'} match
        </p>
      )}
    </Card>
  );
}
