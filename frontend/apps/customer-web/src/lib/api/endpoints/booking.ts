import { bookingApi } from '../client';
import type {
  BookingResponse,
  BookingsResponse,
  CancelBookingResponse,
  CreateBookingRequest,
  CreateBookingResponse,
  HoldSeatsRequest,
  HoldSeatsResponse,
  ReleaseHoldResponse,
  SeatSelectionViewResponse,
  TicketResponse,
} from '../types';

/**
 * booking-service — `adapter/in/rest/{booking,hold,ticket}`.
 * Every route here requires a TRAVELER-role JWT; the controllers reject other roles outright.
 */
export const bookingEndpoints = {
  /** GET /api/v1/bookings/trips/{tripId}/seats — layout plus live per-seat status. */
  async seatView(tripId: string): Promise<SeatSelectionViewResponse> {
    const { data } = await bookingApi.get<SeatSelectionViewResponse>(
      `/api/v1/bookings/trips/${tripId}/seats`,
    );
    return data;
  },

  /** POST /api/v1/bookings/holds — reserves the seats for a bounded window. */
  async holdSeats(body: HoldSeatsRequest): Promise<HoldSeatsResponse> {
    const { data } = await bookingApi.post<HoldSeatsResponse>('/api/v1/bookings/holds', body);
    return data;
  },

  /** DELETE /api/v1/bookings/holds/{seatHoldId} — releases seats when the user backs out. */
  async releaseHold(seatHoldId: string): Promise<ReleaseHoldResponse> {
    const { data } = await bookingApi.delete<ReleaseHoldResponse>(
      `/api/v1/bookings/holds/${seatHoldId}`,
    );
    return data;
  },

  /** POST /api/v1/bookings — consumes the hold, returns a PENDING_PAYMENT booking. */
  async create(body: CreateBookingRequest): Promise<CreateBookingResponse> {
    const { data } = await bookingApi.post<CreateBookingResponse>('/api/v1/bookings', body);
    return data;
  },

  /** GET /api/v1/bookings/{bookingId} */
  async get(bookingId: string): Promise<BookingResponse> {
    const { data } = await bookingApi.get<BookingResponse>(`/api/v1/bookings/${bookingId}`);
    return data;
  },

  /** GET /api/v1/bookings — for a TRAVELER this is their own history, no params needed. */
  async list(): Promise<BookingResponse[]> {
    const { data } = await bookingApi.get<BookingsResponse>('/api/v1/bookings');
    return data.bookings;
  },

  /** POST /api/v1/bookings/{bookingId}/cancel — idempotent. */
  async cancel(bookingId: string): Promise<CancelBookingResponse> {
    const { data } = await bookingApi.post<CancelBookingResponse>(
      `/api/v1/bookings/${bookingId}/cancel`,
    );
    return data;
  },

  /** GET /api/v1/bookings/{bookingId}/ticket — 404 until the provider has issued one. */
  async ticket(bookingId: string): Promise<TicketResponse> {
    const { data } = await bookingApi.get<TicketResponse>(`/api/v1/bookings/${bookingId}/ticket`);
    return data;
  },
};
