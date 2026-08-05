import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

/**
 * Unit tests only — no dev server, no backend. Everything under test here is either a pure
 * function (dashboard aggregation, formatting) or a component rendered against a stubbed API
 * layer. Flows that need a live registry belong in an end-to-end suite, which this app does not
 * have yet.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
