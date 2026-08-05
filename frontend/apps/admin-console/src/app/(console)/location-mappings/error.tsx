'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/feedback';

/**
 * Boundary for the mapping screens.
 *
 * <p>The app-wide `app/error.tsx` would also catch these, but it sends the operator back to the
 * dashboard — losing the provider and filters they had selected. This one keeps them in the
 * section: a render failure here is nearly always one screen's data, and the neighbouring view is
 * usually still fine.
 */
export default function LocationMappingsError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="mx-auto max-w-2xl px-5 py-20 sm:px-8">
      <ErrorState
        error={error}
        title="The mapping screen ran into a problem"
        onRetry={reset}
        retryLabel="Reload this screen"
      />
      {error.digest && (
        <p className="text-center font-mono text-micro text-content-muted">Digest {error.digest}</p>
      )}
      <div className="mt-6 flex justify-center gap-3">
        <Button asChild variant="ghost" size="sm">
          <Link href="/location-mappings">All mappings</Link>
        </Button>
        <Button asChild variant="ghost" size="sm">
          <Link href="/providers">Providers</Link>
        </Button>
      </div>
    </div>
  );
}
