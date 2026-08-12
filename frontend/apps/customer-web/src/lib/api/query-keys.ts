import type { SearchTripsParams } from './types';

/** Central key factory — every cache read/invalidation goes through here, never a literal array. */
export const queryKeys = {
  trips: {
    all: ['trips'] as const,
    search: (params: SearchTripsParams) => ['trips', 'search', params] as const,
    detail: (tripId: string) => ['trips', 'detail', tripId] as const,
    suggestions: (query: string) => ['trips', 'suggestions', query] as const,
  },
  locations: {
    search: (query: string) => ['locations', 'search', query] as const,
  },
  seats: {
    view: (tripId: string) => ['seats', tripId] as const,
  },
  bookings: {
    all: ['bookings'] as const,
    list: () => ['bookings', 'list'] as const,
    detail: (bookingId: string) => ['bookings', 'detail', bookingId] as const,
    ticket: (bookingId: string) => ['bookings', 'ticket', bookingId] as const,
  },
  payments: {
    detail: (paymentId: string) => ['payments', 'detail', paymentId] as const,
    status: (paymentId: string) => ['payments', 'status', paymentId] as const,
  },
} as const;
