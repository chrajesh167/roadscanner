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
  // Emits .next/standalone with a self-contained server.js and only the node_modules actually
  // reachable from the build. The container image copies that instead of the full dependency
  // tree — the difference is hundreds of megabytes, and nothing about local `next dev` changes.
  output: 'standalone',
  experimental: {
    optimizePackageImports: ['lucide-react', 'framer-motion'],
  },
};

export default nextConfig;
