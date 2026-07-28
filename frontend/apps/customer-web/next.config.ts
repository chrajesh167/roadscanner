import type { NextConfig } from 'next';

/**
 * `api-gateway` is not implemented yet (it exposes no routes today), so the browser talks to each
 * service directly on its own port. Every service's `application-local.yml` allows
 * `http://localhost:5173` as a CORS origin, which is why `dev`/`start` pin that port — running on
 * Next's default 3000 would be rejected by the backend without a backend change.
 *
 * When `api-gateway` lands, point every `NEXT_PUBLIC_*_API_URL` at it and delete nothing else:
 * the API layer already reads all four from env (see src/lib/api/config.ts).
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  experimental: {
    optimizePackageImports: ['lucide-react', 'framer-motion'],
  },
};

export default nextConfig;
