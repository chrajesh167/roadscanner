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
  // Emits .next/standalone with a self-contained server.js and only the node_modules actually
  // reachable from the build. The container image copies that instead of the full dependency
  // tree — the difference is hundreds of megabytes, and nothing about local `next dev` changes.
  output: 'standalone',
  // The console is served under /admin/ behind the production ingress, sharing one origin with
  // customer-web (docker/nginx/nginx.conf).
  //
  // This has to be Next's own basePath rather than a proxy rewrite. Next prefixes routes, <Link>
  // targets, router.push/redirect and — decisively — its own /_next/* asset URLs with this value.
  // Stripping the prefix at the proxy instead would leave the app requesting /_next/* at the root,
  // where the ingress serves customer-web, and the console would silently load the wrong bundles.
  //
  // Local development moves with it: `npm run dev` now serves http://localhost:5174/admin.
  basePath: '/admin',
  experimental: {
    optimizePackageImports: ['lucide-react', 'framer-motion'],
  },
};

export default nextConfig;
