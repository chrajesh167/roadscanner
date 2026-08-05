/**
 * Wire types. Each interface mirrors a Java record one-for-one — same field names, same
 * nullability. Nothing here is invented: if a field is not returned by a controller today, it does
 * not appear here.
 *
 * Sources:
 *   auth-service                  adapter/in/rest/{login,token}
 *   provider-integration-service  adapter/in/rest/{admin,health}
 */

// ---------------------------------------------------------------------------
// Shared error shape — every service uses this `ErrorResponse`
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

// ---------------------------------------------------------------------------
// provider-integration-service — admin registry
// ---------------------------------------------------------------------------

/**
 * `ProviderCapability` is a Java enum, so this union is closed and the values are exact. It is
 * the one place capability strings are spelled out; the registry form, the dashboard breakdown
 * and the detail view all read from here.
 */
export const PROVIDER_CAPABILITIES = [
  'SEARCH',
  'SEAT_MAP',
  'SEAT_BLOCK',
  'SEAT_RELEASE',
  'BOOKING_CONFIRMATION',
  'BOOKING_CANCELLATION',
  'ORDER_DETAILS',
  'TICKET_DOWNLOAD',
  'HEALTH_CHECK',
] as const;

export type ProviderCapability = (typeof PROVIDER_CAPABILITIES)[number];

/**
 * `ProviderCategory` is an open value object, not an enum — any non-blank code up to 50
 * characters, upper-cased by the backend. These three are the constants it names, offered as
 * suggestions rather than as a closed list, because registering a fourth vertical must not
 * require a frontend change.
 */
export const SUGGESTED_PROVIDER_CATEGORIES = ['BUS', 'RAIL', 'AIRLINE'] as const;

/** `ProviderResponse` — carries no credential of any kind, by design. */
export interface ProviderResponse {
  id: string;
  code: string;
  category: string;
  displayName: string;
  enabled: boolean;
  /** Alphabetical, guaranteed stable by the backend. */
  capabilities: ProviderCapability[];
  baseUrl: string | null;
  timeoutMs: number;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
}

/** `ProviderRequest`. `code` is required on create and ignored on update. */
export interface ProviderRequest {
  code?: string;
  category: string;
  displayName: string;
  capabilities: ProviderCapability[];
  baseUrl?: string | null;
  timeoutMs?: number;
  retryCount?: number;
}

/**
 * `ProviderCredentialsResponse` — presence and freshness only. There is deliberately no field
 * here that could hold a secret, and the backend has no endpoint that returns one.
 */
export interface ProviderCredentialsResponse {
  hasPassword: boolean;
  hasToken: boolean;
  encrypted: boolean;
  updatedAt: string;
}

/** `ProviderCredentialsRequest` — write-only. A full replacement, not a patch. */
export interface ProviderCredentialsRequest {
  partnerEmail?: string | null;
  partnerPassword?: string | null;
  partnerToken?: string | null;
}

/** `ProviderSessionSummaryResponse` — never carries the session's access token. */
export interface ProviderSessionSummaryResponse {
  sessionId: string;
  expiresAt: string;
}

// ---------------------------------------------------------------------------
// provider-integration-service — health
// ---------------------------------------------------------------------------

/** `HealthState` is a Java enum on `ProviderHealth`. `UNAVAILABLE`, not "unhealthy". */
export type HealthState = 'UNKNOWN' | 'HEALTHY' | 'DEGRADED' | 'UNAVAILABLE';

/**
 * `ProviderHealthResponse`. Returned by both the internal probe route and
 * `POST /api/v1/providers/{id}/test` — the same record, so one type serves both.
 */
export interface ProviderHealthResponse {
  providerType: string;
  currentState: HealthState;
  lastCheckedAt: string;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  consecutiveFailures: number;
}
