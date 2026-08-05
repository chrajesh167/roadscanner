'use client';

/**
 * The last boundary. `error.tsx` renders inside the root layout, so a failure in the layout itself
 * (or in a provider it mounts) would escape it — this one replaces the whole document and
 * therefore cannot rely on any of the app's styling or context.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="en">
      <body
        style={{
          minHeight: '100dvh',
          margin: 0,
          display: 'grid',
          placeItems: 'center',
          background: '#0a0c11',
          color: '#f4f6f8',
          fontFamily: 'ui-sans-serif, system-ui, -apple-system, sans-serif',
          padding: '2rem',
        }}
      >
        <div style={{ maxWidth: '28rem', textAlign: 'center' }}>
          <h1 style={{ fontSize: '1.5rem', marginBottom: '0.75rem' }}>The console failed to load</h1>
          <p style={{ color: '#b3bac7', lineHeight: 1.6, marginBottom: '1.5rem' }}>
            {error.message || 'An unexpected error occurred before the interface could start.'}
          </p>
          {error.digest && (
            <p style={{ color: '#8b93a3', fontFamily: 'ui-monospace, monospace', fontSize: '0.75rem' }}>
              Digest {error.digest}
            </p>
          )}
          <button
            onClick={reset}
            style={{
              marginTop: '1.5rem',
              padding: '0.625rem 1.25rem',
              borderRadius: '12px',
              border: '1px solid rgba(255,255,255,0.14)',
              background: '#8b6dff',
              color: '#fff',
              fontSize: '1rem',
              cursor: 'pointer',
            }}
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
