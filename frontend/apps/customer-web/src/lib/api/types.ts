/**
 * Wire types. Each interface mirrors a Java record in the backend one-for-one — same field names,
 * same nullability. Nothing here is invented: if a field is not returned by a controller today, it
 * does not appear here.
 *
 * Sources:
 *   auth-service    adapter/in/rest/{login,registration,token}
 *   search-service  adapter/in/rest/{search,detail,suggestion}
 *   booking-service adapter/in/rest/{booking,hold,ticket}
 *   payment-service adapter/in/rest/payment
 */

// ---------------------------------------------------------------------------
// Shared error shape — auth/search/booking/payment all use this `ErrorResponse`
// ---------------------------------------------------------------------------

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId: string;
  fieldErrors: FieldError[] | null;
}

// ---------------------------------------------------------------------------
// auth-service
// ---------------------------------------------------------------------------

/** `identifier` is an email or phone number — the backend accepts either. */
export interface LoginRequest {
  identifier: string;
  password: string;
  deviceLabel?: string;
}

export interface RegisterRequest {
  identifier: string;
  password: string;
  deviceLabel?: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export type Role = 'TRAVELER' | 'OPERATOR' | 'ADMIN' | 'SUPPORT';

export interface AuthTokensResponse {
  userId: string;
  role: Role;
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface RequestPasswordResetRequest {
  identifier: string;
}

// ---------------------------------------------------------------------------
// search-service
// ---------------------------------------------------------------------------

export type SortOption = 'PRICE_ASC' | 'PRICE_DESC' | 'DEPARTURE_TIME_ASC' | 'DURATION_ASC';

export interface SearchTripsParams {
  origin: string;
  destination: string;
  date: string; // yyyy-MM-dd
  minFare?: number;
  maxFare?: number;
  departureAfter?: string; // Instant
  departureBefore?: string; // Instant
  busType?: string;
  minRating?: number;
  sort?: SortOption;
  page?: number;
  size?: number;
}

export interface TripResponse {
  tripId: string;
  operatorId: string;
  operatorName: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  durationMinutes: number;
  busTypeCategory: string;
  amenities: string[];
  fareAmount: string;
  fareCurrency: string;
  bookable: boolean;
  ratingAverage: number;
  ratingReviewCount: number;
  /** null when the live availability overlay could not be resolved — see `availabilityKnown`. */
  availableSeats: number | null;
  availabilityKnown: boolean;
}

export interface SearchResultResponse {
  content: TripResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SearchSuggestionsResponse {
  suggestions: string[];
}

// ---------------------------------------------------------------------------
// booking-service
// ---------------------------------------------------------------------------

/** Mirrors `SeatViewResponse`. `status` is the live seat state from the provider. */
export interface SeatViewResponse {
  seatNumber: string;
  deck: string;
  seatType: string;
  wheelchairAccessible: boolean;
  position: number | null;
  status: string;
  priceAmount: string;
  priceCurrency: string;
}

export interface SeatSelectionViewResponse {
  seats: SeatViewResponse[];
}

export interface HoldSeatsRequest {
  tripId: string;
  seatNumbers: string[];
}

export interface HoldSeatsResponse {
  seatHoldId: string;
  seatNumbers: string[];
  expiresAt: string;
}

export interface ReleaseHoldResponse {
  released: boolean;
}

export interface PassengerRequest {
  fullName: string;
  age: number;
  gender: string;
  seatNumber: string;
}

export interface CreateBookingRequest {
  seatHoldId: string;
  passengers: PassengerRequest[];
}

export type BookingStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'COMPLETED';

export interface CreateBookingResponse {
  bookingId: string;
  status: string;
}

export interface PassengerResponse {
  fullName: string;
  age: number;
  gender: string;
  seatNumber: string;
}

export interface BookingResponse {
  bookingId: string;
  travelerId: string;
  tripId: string;
  providerBookingReference: string | null;
  passengers: PassengerResponse[];
  fareAmount: string;
  fareCurrency: string;
  status: string;
  cancellationReason: string | null;
  supportFlagged: boolean;
  createdAt: string;
  confirmedAt: string | null;
  cancelledAt: string | null;
  completedAt: string | null;
}

export interface BookingsResponse {
  bookings: BookingResponse[];
}

export interface CancelBookingResponse {
  status: string;
}

/** `contentBase64` is the raw ticket payload; `format` is the provider's media type. */
export interface TicketResponse {
  providerTicketId: string;
  format: string;
  contentBase64: string;
  issuedAt: string;
}

// ---------------------------------------------------------------------------
// payment-service
// ---------------------------------------------------------------------------

export type PaymentMethod = 'CARD' | 'UPI' | 'WALLET' | 'NETBANKING';

export type PaymentStatus =
  | 'CREATED'
  | 'PENDING'
  | 'AUTHORIZED'
  | 'CAPTURED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REFUND_PENDING'
  | 'REFUNDED';

/** `bookingReference` is the bookingId returned by `POST /api/v1/bookings`. */
export interface InitiatePaymentRequest {
  bookingReference: string;
  amount: string;
  currency: string;
  method: PaymentMethod;
  gatewayType?: string;
}

export interface InitiatePaymentResponse {
  paymentId: string;
  status: string;
}

export interface PaymentResponse {
  paymentId: string;
  bookingReference: string;
  amount: string;
  currency: string;
  method: string;
  gatewayType: string;
  status: string;
  attempts: number;
  createdAt: string;
}

export interface PaymentStatusResponse {
  paymentId: string;
  status: string;
}
