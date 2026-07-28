'use client';

import * as React from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { AnimatePresence, motion } from 'framer-motion';
import { ChevronLeft, ChevronRight, SearchX, SlidersHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { EmptyState, ErrorState } from '@/components/ui/feedback';
import { PageShell } from '@/components/layout/page-shell';
import { SearchForm } from '@/components/search/search-form';
import { ResultFilters, type FilterValues } from '@/components/search/result-filters';
import { TripCard, TripCardSkeleton } from '@/components/search/trip-card';
import { useSearchTrips } from '@/lib/hooks/use-search';
import { formatRelativeDay } from '@/lib/utils/format';
import type { SearchTripsParams, SortOption } from '@/lib/api/types';

const PAGE_SIZE = 10;

export function ResultsView() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const origin = searchParams.get('origin') ?? '';
  const destination = searchParams.get('destination') ?? '';
  const date = searchParams.get('date') ?? '';
  const page = Number.parseInt(searchParams.get('page') ?? '0', 10) || 0;

  const [filters, setFilters] = React.useState<FilterValues>(() => ({
    minFare: searchParams.get('minFare') ?? '',
    maxFare: searchParams.get('maxFare') ?? '',
    busType: searchParams.get('busType') ?? '',
    minRating: searchParams.get('minRating') ?? '',
    sort: (searchParams.get('sort') as SortOption | null) ?? '',
  }));

  // Filters are debounced into the query so typing a fare bound doesn't fire a request per digit.
  const [appliedFilters, setAppliedFilters] = React.useState(filters);
  React.useEffect(() => {
    const timer = setTimeout(() => setAppliedFilters(filters), 300);
    return () => clearTimeout(timer);
  }, [filters]);

  const hasRoute = Boolean(origin && destination && date);

  const params: SearchTripsParams | null = React.useMemo(() => {
    if (!hasRoute) return null;
    const numeric = (value?: string) => {
      const parsed = Number.parseFloat(value ?? '');
      return Number.isFinite(parsed) ? parsed : undefined;
    };
    return {
      origin,
      destination,
      date,
      page,
      size: PAGE_SIZE,
      minFare: numeric(appliedFilters.minFare),
      maxFare: numeric(appliedFilters.maxFare),
      busType: appliedFilters.busType?.trim() || undefined,
      minRating: numeric(appliedFilters.minRating),
      sort: appliedFilters.sort || undefined,
    };
  }, [hasRoute, origin, destination, date, page, appliedFilters]);

  const { data, isLoading, isFetching, isError, error, refetch } = useSearchTrips(params);

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams.toString());
    next.set('page', String(nextPage));
    router.push(`/search/results?${next.toString()}`);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function resetFilters() {
    setFilters({ minFare: '', maxFare: '', busType: '', minRating: '', sort: '' });
  }

  if (!hasRoute) {
    return (
      <PageShell title="Search results">
        <EmptyState
          icon={<SearchX />}
          title="No route selected"
          description="Tell us where you're travelling from and to, and on which date."
          action={
            <Button asChild>
              <Link href="/search">Start a search</Link>
            </Button>
          }
        />
      </PageShell>
    );
  }

  const filterPanel = (
    <ResultFilters
      values={filters}
      onChange={setFilters}
      onReset={resetFilters}
      resultCount={data?.totalElements}
    />
  );

  return (
    <PageShell
      width="wide"
      backHref="/search"
      backLabel="New search"
      title={`${origin} → ${destination}`}
      description={
        <>
          {formatRelativeDay(date)}
          {data && (
            <span className="text-content-muted">
              {' · '}
              {data.totalElements} {data.totalElements === 1 ? 'trip' : 'trips'}
            </span>
          )}
        </>
      }
      actions={
        <div className="lg:hidden">
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="secondary" size="sm">
                <SlidersHorizontal />
                Filters
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-sm">
              <DialogHeader>
                <DialogTitle>Refine results</DialogTitle>
              </DialogHeader>
              {filterPanel}
            </DialogContent>
          </Dialog>
        </div>
      }
    >
      <div className="mb-8">
        <SearchForm variant="compact" defaultValues={{ origin, destination, date }} />
      </div>

      <div className="grid gap-8 lg:grid-cols-[280px_minmax(0,1fr)] lg:items-start">
        <aside className="hidden lg:block lg:sticky lg:top-24">{filterPanel}</aside>

        <section aria-live="polite" aria-busy={isFetching}>
          {isLoading ? (
            <div className="flex flex-col gap-4">
              {Array.from({ length: 4 }).map((_, index) => (
                <TripCardSkeleton key={index} />
              ))}
            </div>
          ) : isError ? (
            <ErrorState error={error} onRetry={() => void refetch()} />
          ) : !data || data.content.length === 0 ? (
            <EmptyState
              icon={<SearchX />}
              title="No trips on this route"
              description="Nothing matches this date and these filters. Try a nearby date or clear your filters."
              action={
                <Button variant="secondary" onClick={resetFilters}>
                  Clear filters
                </Button>
              }
            />
          ) : (
            <>
              {/* The list fades while a refinement is in flight rather than being torn down —
                  keepPreviousData keeps the old page mounted underneath. */}
              <motion.div
                className="flex flex-col gap-4"
                animate={{ opacity: isFetching ? 0.55 : 1 }}
                transition={{ duration: 0.2 }}
              >
                <AnimatePresence mode="popLayout">
                  {data.content.map((trip, index) => (
                    <motion.div
                      key={trip.tripId}
                      layout
                      initial={{ opacity: 0, y: 12 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.35, delay: index * 0.04, ease: [0.22, 1, 0.36, 1] }}
                    >
                      <TripCard trip={trip} />
                    </motion.div>
                  ))}
                </AnimatePresence>
              </motion.div>

              {data.totalPages > 1 && (
                <nav
                  className="mt-8 flex items-center justify-between gap-4"
                  aria-label="Result pages"
                >
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={page <= 0}
                    onClick={() => goToPage(page - 1)}
                  >
                    <ChevronLeft />
                    Previous
                  </Button>
                  <p className="text-caption text-content-muted">
                    Page {data.page + 1} of {data.totalPages}
                  </p>
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={page >= data.totalPages - 1}
                    onClick={() => goToPage(page + 1)}
                  >
                    Next
                    <ChevronRight />
                  </Button>
                </nav>
              )}
            </>
          )}
        </section>
      </div>
    </PageShell>
  );
}
