'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { PassengerRequest, TripResponse } from '@/lib/api/types';

/**
 * The in-progress booking, carried across the four screens between seat selection and payment.
 *
 * It is persisted deliberately: a hold is a real, expiring server-side reservation, so an
 * accidental refresh on the passenger or payment screen must not orphan it. `expiresAt` is the
 * authority on whether the flow is still alive — the client never extends it.
 *
 * `selectedSeats` exists because a hold can no longer be placed at seat selection: the provider
 * binds an occupant to a seat when the block is made, so the seats are merely *chosen* until the
 * traveller names have been entered. Between those two screens the seats are not reserved for
 * anyone, which is why the passenger screen has to be ready for a seat to be gone at submit.
 *
 * `idempotencyKey` is minted once per booking and reused for every payment retry on that booking,
 * which is what makes a double-submit return the existing payment instead of charging twice.
 */
export interface HeldSeats {
  seatHoldId: string;
  seatNumbers: string[];
  expiresAt: string;
}

interface BookingFlowState {
  trip: TripResponse | null;
  selectedSeats: string[];
  hold: HeldSeats | null;
  passengers: PassengerRequest[];
  bookingId: string | null;
  idempotencyKey: string | null;

  startFlow: (trip: TripResponse) => void;
  setSelectedSeats: (seatNumbers: string[]) => void;
  setHold: (hold: HeldSeats) => void;
  setPassengers: (passengers: PassengerRequest[]) => void;
  setBooking: (bookingId: string) => void;
  reset: () => void;
}

type BookingFlowData = Pick<
  BookingFlowState,
  'trip' | 'selectedSeats' | 'hold' | 'passengers' | 'bookingId' | 'idempotencyKey'
>;

// A factory, not a shared constant — each reset must get its own array fields.
function emptyFlow(): BookingFlowData {
  return {
    trip: null,
    selectedSeats: [],
    hold: null,
    passengers: [],
    bookingId: null,
    idempotencyKey: null,
  };
}

function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export const useBookingFlowStore = create<BookingFlowState>()(
  persist(
    (set) => ({
      ...emptyFlow(),

      // Selecting a new trip abandons any earlier in-flight flow rather than merging into it.
      startFlow: (trip) => set({ ...emptyFlow(), trip }),

      setSelectedSeats: (seatNumbers) => set({ selectedSeats: seatNumbers }),

      setHold: (hold) => set({ hold }),

      setPassengers: (passengers) => set({ passengers }),

      setBooking: (bookingId) => set({ bookingId, idempotencyKey: newIdempotencyKey() }),

      reset: () => set(emptyFlow()),
    }),
    { name: 'roadscanner.booking-flow' },
  ),
);

/** True once the server-side hold window has elapsed; the flow cannot proceed past this. */
export function isHoldExpired(hold: HeldSeats | null): boolean {
  if (!hold) return true;
  return new Date(hold.expiresAt).getTime() <= Date.now();
}
