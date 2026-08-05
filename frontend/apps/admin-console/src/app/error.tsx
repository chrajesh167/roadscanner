'use client';

import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/feedback';

/** Route-level boundary. Catches render-time failures the query error states don't cover. */
export default function RouteError({
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
        title="This screen ran into a problem"
        onRetry={reset}
        retryLabel="Reload this screen"
      />
      {error.digest && (
        <p className="text-center font-mono text-micro text-content-muted">Digest {error.digest}</p>
      )}
      <div className="mt-6 flex justify-center">
        <Button variant="ghost" size="sm" onClick={() => window.location.assign('/dashboard')}>
          Back to dashboard
        </Button>
      </div>
    </div>
  );
}
