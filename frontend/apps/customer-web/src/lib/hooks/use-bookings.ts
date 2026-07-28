'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { bookingEndpoints } from '@/lib/api/endpoints/booking';
import { ApiError } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/query-keys';
import { useAuthStore } from '@/lib/store/auth-store';
import type { CreateBookingRequest, HoldSeatsRequest } from '@/lib/api/types';

function useIsTraveler(): boolean {
  const session = useAuthStore((s) => s.session);
  return session?.role === 'TRAVELER';
}

export function useSeatView(tripId: string | null) {
  const enabled = useIsTraveler() && Boolean(tripId);
  return useQuery({
    queryKey: queryKeys.seats.view(tripId ?? ''),
    queryFn: () => bookingEndpoints.seatView(tripId!),
    enabled,
    // Seat status is live and contended — never serve it from a warm cache on remount.
    staleTime: 0,
    refetchOnWindowFocus: true,
  });
}

export function useBookings() {
  const enabled = useIsTraveler();
  return useQuery({
    queryKey: queryKeys.bookings.list(),
    queryFn: () => bookingEndpoints.list(),
    enabled,
    staleTime: 30_000,
  });
}

export function useBooking(bookingId: string | null) {
  return useQuery({
    queryKey: queryKeys.bookings.detail(bookingId ?? ''),
    queryFn: () => bookingEndpoints.get(bookingId!),
    enabled: Boolean(bookingId),
  });
}

/**
 * A ticket only exists once the provider has issued one, so a 404 here is an expected state
 * ("not issued yet"), not a failure worth retrying.
 */
export function useTicket(bookingId: string | null, enabled = true) {
  return useQuery({
    queryKey: queryKeys.bookings.ticket(bookingId ?? ''),
    queryFn: () => bookingEndpoints.ticket(bookingId!),
    enabled: Boolean(bookingId) && enabled,
    retry: (failureCount, error) =>
      error instanceof ApiError && error.status === 404 ? false : failureCount < 2,
  });
}

export function useHoldSeats() {
  return useMutation({
    mutationFn: (body: HoldSeatsRequest) => bookingEndpoints.holdSeats(body),
  });
}

export function useReleaseHold() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (seatHoldId: string) => bookingEndpoints.releaseHold(seatHoldId),
    onSuccess: () => {
      // Released seats are immediately bookable again by anyone, this session included.
      void queryClient.invalidateQueries({ queryKey: ['seats'] });
    },
  });
}

export function useCreateBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateBookingRequest) => bookingEndpoints.create(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.bookings.all });
    },
  });
}

export function useCancelBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (bookingId: string) => bookingEndpoints.cancel(bookingId),
    onSuccess: (_result, bookingId) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.bookings.detail(bookingId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.bookings.list() });
    },
  });
}
