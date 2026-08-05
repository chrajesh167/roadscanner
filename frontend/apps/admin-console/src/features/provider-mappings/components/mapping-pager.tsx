'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';

/**
 * Page controls for the mapping table.
 *
 * <p>Deliberately prev/next with a position readout rather than numbered pages. The backend orders
 * by most-recently-updated, so page numbers are not stable landmarks — editing a mapping moves it
 * to the front and shifts everything after it. "Page 2 of 7" describes where you are; a numbered
 * jump would imply the pages hold fixed contents.
 */
export function MappingPager({
  page,
  size,
  totalElements,
  totalPages,
  busy,
  onPageChange,
}: {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  busy: boolean;
  onPageChange: (page: number) => void;
}) {
  if (totalElements === 0) return null;

  const first = page * size + 1;
  const last = Math.min(totalElements, (page + 1) * size);

  return (
    <div className="mt-4 flex flex-col items-center justify-between gap-3 sm:flex-row">
      <p
        className="text-caption text-content-muted"
        aria-live="polite"
        data-testid="mapping-pager-summary"
      >
        Showing <span className="text-content-secondary">{first}</span>–
        <span className="text-content-secondary">{last}</span> of{' '}
        <span className="text-content-secondary">{totalElements}</span>
        {totalPages > 1 && (
          <>
            {' · '}page {page + 1} of {totalPages}
          </>
        )}
      </p>

      <div className="flex items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          disabled={page === 0 || busy}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft />
          Previous
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={page + 1 >= totalPages || busy}
          onClick={() => onPageChange(page + 1)}
        >
          Next
          <ChevronRight />
        </Button>
      </div>
    </div>
  );
}
