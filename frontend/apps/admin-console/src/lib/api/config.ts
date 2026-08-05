/**
 * Service base URLs.
 *
 * The console needs three services and deliberately reaches for nothing else — an admin tool that
 * can call every service is a much larger blast radius than one that cannot:
 *
 *   auth-service                  signing in
 *   provider-integration-service  the provider registry itself
 *   search-service                the location catalogue and its provider translation layer
 *
 * The third is not a second registry. `search-service` owns canonical locations and the
 * location↔provider mapping table; `provider-integration-service` owns everything else about a
 * provider. The console is the one place both are on screen at once, which is exactly why it must
 * keep asking each service for what that service owns.
 *
 * There is no `api-gateway` yet (the service exists but exposes no routes), so the browser
 * addresses each service on the port its `application.yml` declares. When a gateway lands, all
 * three point at the same origin and nothing else in the app changes.
 */
export const API = {
  auth: process.env.NEXT_PUBLIC_AUTH_API_URL ?? 'http://localhost:8081',
  providerIntegration:
    process.env.NEXT_PUBLIC_PROVIDER_INTEGRATION_API_URL ?? 'http://localhost:8083',
  search: process.env.NEXT_PUBLIC_SEARCH_API_URL ?? 'http://localhost:8082',
} as const;

/** Every service reads this header in its `CorrelationIdFilter` and echoes it back on errors. */
export const CORRELATION_ID_HEADER = 'X-Correlation-Id';
