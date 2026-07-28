'use client';

import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { API, CORRELATION_ID_HEADER } from './config';
import { clearSession, getSession, useAuthStore } from '@/lib/store/auth-store';
import type { AuthTokensResponse, ErrorResponse, FieldError } from './types';

/**
 * One axios instance per service, all sharing the same interceptor pair:
 *
 *   request  — attach the bearer token and a correlation id
 *   response — normalise the backend `ErrorResponse` into `ApiError`, and transparently refresh a
 *              401 exactly once before replaying the original request
 *
 * Refresh is single-flight: concurrent 401s queue behind one `POST /api/v1/auth/refresh` rather
 * than each firing their own and racing to rotate the refresh token.
 */

/** A normalised failure. Every rejected request from this layer is an `ApiError`. */
export class ApiError extends Error {
  readonly status: number;
  readonly correlationId: string | null;
  readonly fieldErrors: FieldError[];
  readonly isNetworkError: boolean;

  constructor(params: {
    message: string;
    status: number;
    correlationId?: string | null;
    fieldErrors?: FieldError[];
    isNetworkError?: boolean;
  }) {
    super(params.message);
    this.name = 'ApiError';
    this.status = params.status;
    this.correlationId = params.correlationId ?? null;
    this.fieldErrors = params.fieldErrors ?? [];
    this.isNetworkError = params.isNetworkError ?? false;
  }

  /** Backend is unreachable — worth telling the user to check that the services are running. */
  static isOffline(error: unknown): boolean {
    return error instanceof ApiError && error.isNetworkError;
  }
}

function correlationId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function toApiError(error: AxiosError<ErrorResponse>): ApiError {
  if (error.response) {
    const body = error.response.data;
    return new ApiError({
      message: body?.message || fallbackMessage(error.response.status),
      status: error.response.status,
      correlationId: body?.correlationId ?? null,
      fieldErrors: body?.fieldErrors ?? [],
    });
  }

  return new ApiError({
    message: "We can't reach RoadScanner right now. Check your connection and try again.",
    status: 0,
    isNetworkError: true,
  });
}

function fallbackMessage(status: number): string {
  switch (status) {
    case 400:
      return 'That request was not valid. Please review the highlighted fields.';
    case 401:
      return 'Your session has expired. Please sign in again.';
    case 403:
      return "You don't have permission to do that.";
    case 404:
      return "We couldn't find what you were looking for.";
    case 409:
      return 'That conflicts with the current state. Refresh and try again.';
    case 429:
      return 'Too many requests. Please wait a moment.';
    default:
      return status >= 500 ? 'Something went wrong on our side.' : 'Something went wrong.';
  }
}

// --- single-flight refresh ---------------------------------------------------

let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const session = getSession();
  if (!session) return null;

  try {
    // A bare axios call, not `authApi` — going through the instance would re-enter these
    // interceptors and could recurse on a failing refresh.
    const { data } = await axios.post<AuthTokensResponse>(
      `${API.auth}/api/v1/auth/refresh`,
      { refreshToken: session.refreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    );
    useAuthStore.getState().updateTokens(data);
    return data.accessToken;
  } catch {
    clearSession();
    return null;
  }
}

function requestRefresh(): Promise<string | null> {
  refreshInFlight ??= refreshAccessToken().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

// --- instance factory --------------------------------------------------------

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
  /** Set on endpoints that must not trigger a refresh (login, register, refresh itself). */
  _skipAuthRefresh?: boolean;
}

function createClient(baseURL: string): AxiosInstance {
  const instance = axios.create({
    baseURL,
    timeout: 20_000,
    headers: { 'Content-Type': 'application/json' },
  });

  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const session = getSession();
    if (session) {
      config.headers.set('Authorization', `Bearer ${session.accessToken}`);
    }
    config.headers.set(CORRELATION_ID_HEADER, correlationId());
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ErrorResponse>) => {
      const config = error.config as RetriableConfig | undefined;

      const canRetry =
        error.response?.status === 401 &&
        config &&
        !config._retried &&
        !config._skipAuthRefresh &&
        getSession() !== null;

      if (canRetry) {
        config._retried = true;
        const token = await requestRefresh();
        if (token) {
          config.headers.set('Authorization', `Bearer ${token}`);
          return instance.request(config);
        }
      }

      return Promise.reject(toApiError(error));
    },
  );

  return instance;
}

export const authApi = createClient(API.auth);
export const searchApi = createClient(API.search);
export const inventoryApi = createClient(API.inventory);
export const bookingApi = createClient(API.booking);
export const paymentApi = createClient(API.payment);

/** Marks a request as exempt from the refresh-and-replay path. */
export function withoutAuthRefresh(config: AxiosRequestConfig = {}): AxiosRequestConfig {
  return { ...config, _skipAuthRefresh: true } as AxiosRequestConfig;
}
