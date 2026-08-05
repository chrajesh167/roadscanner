/**
 * Service base URLs.
 *
 * The console needs exactly two services: `auth-service` to sign in, and
 * `provider-integration-service` for the registry itself. It deliberately reaches for nothing
 * else — an admin tool that can call every service is a much larger blast radius than one that
 * cannot.
 *
 * There is no `api-gateway` yet (the service exists but exposes no routes), so the browser
 * addresses each service on the port its `application.yml` declares. When a gateway lands, both
 * of these point at the same origin and nothing else in the app changes.
 */
export const API = {
  auth: process.env.NEXT_PUBLIC_AUTH_API_URL ?? 'http://localhost:8081',
  providerIntegration:
    process.env.NEXT_PUBLIC_PROVIDER_INTEGRATION_API_URL ?? 'http://localhost:8083',
} as const;

/** Every service reads this header in its `CorrelationIdFilter` and echoes it back on errors. */
export const CORRELATION_ID_HEADER = 'X-Correlation-Id';
