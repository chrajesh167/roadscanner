'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { searchEndpoints } from '@/lib/api/endpoints/search';
import { queryKeys } from '@/lib/api/query-keys';
import type { SearchTripsParams } from '@/lib/api/types';

export function useSearchTrips(params: SearchTripsParams | null) {
  return useQuery({
    queryKey: queryKeys.trips.search(params ?? ({} as SearchTripsParams)),
    queryFn: () => searchEndpoints.searchTrips(params!),
    enabled: params !== null,
    // Filter and page changes keep the previous page visible while the next loads — no flash of
    // an empty results list between refinements.
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useTripDetail(tripId: string | null) {
  return useQuery({
    queryKey: queryKeys.trips.detail(tripId ?? ''),
    queryFn: () => searchEndpoints.getTrip(tripId!),
    enabled: Boolean(tripId),
    staleTime: 60_000,
  });
}

export function useSuggestions(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.trips.suggestions(trimmed),
    queryFn: () => searchEndpoints.suggestions(trimmed, 8),
    // The backend requires a non-blank query; below two characters the results are noise anyway.
    enabled: trimmed.length >= 2,
    staleTime: 5 * 60_000,
  });
}
