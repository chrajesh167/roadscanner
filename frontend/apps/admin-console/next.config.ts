import type { NextConfig } from 'next';

/**
 * The console talks to `provider-integration-service` (and `auth-service` for sign-in) directly,
 * because `api-gateway` exposes no routes yet.
 *
 * `dev`/`start` pin port 5174 deliberately: every service's `application-local.yml` allow-lists
 * `http://localhost:5173` (customer-web) and `http://localhost:5174` as CORS origins. Running on
 * Next's default 3000 would fail every request at the preflight without a backend change.
 *
 * When `api-gateway` lands, point both `NEXT_PUBLIC_*_API_URL` values at it — see
 * `src/lib/api/config.ts`. Nothing else in the app knows a hostname.
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  experimental: {
    optimizePackageImports: ['lucide-react', 'framer-motion'],
  },
};

export default nextConfig;
