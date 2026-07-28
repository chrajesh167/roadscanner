/**
 * Service base URLs.
 *
 * There is no `api-gateway` yet (the service exists but exposes no routes), so the browser
 * addresses each service on the port its `application.yml` declares. The moment a gateway lands,
 * every one of these can point at the same origin with no other change in the app.
 */
export const API = {
  auth: process.env.NEXT_PUBLIC_AUTH_API_URL ?? 'http://localhost:8081',
  search: process.env.NEXT_PUBLIC_SEARCH_API_URL ?? 'http://localhost:8082',
  inventory: process.env.NEXT_PUBLIC_INVENTORY_API_URL ?? 'http://localhost:8084',
  booking: process.env.NEXT_PUBLIC_BOOKING_API_URL ?? 'http://localhost:8085',
  payment: process.env.NEXT_PUBLIC_PAYMENT_API_URL ?? 'http://localhost:8086',
} as const;

/** Every service reads this header in its `CorrelationIdFilter` and echoes it back on errors. */
export const CORRELATION_ID_HEADER = 'X-Correlation-Id';
