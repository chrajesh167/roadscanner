'use client';

import * as React from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, Inbox, Loader2, RefreshCw, WifiOff } from 'lucide-react';
import { ApiError } from '@/lib/api/client';
import { cn } from '@/lib/utils/cn';
import { Button } from './button';

// ---------------------------------------------------------------------------
// Skeletons — the loading vocabulary. Every list/detail screen has a skeleton
// that matches its real layout, so nothing jumps when data lands.
// ---------------------------------------------------------------------------

export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden
      className={cn('shimmer relative overflow-hidden rounded-sm bg-white/[0.045]', className)}
      {...props}
    />
  );
}

export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={cn('h-3.5', i === lines - 1 ? 'w-2/5' : 'w-full')} />
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Loaders
// ---------------------------------------------------------------------------

export function Spinner({ className, label }: { className?: string; label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2">
      <Loader2 className={cn('size-4 animate-spin text-accent', className)} aria-hidden />
      <span className={label ? 'text-caption text-content-secondary' : 'sr-only'}>
        {label ?? 'Loading'}
      </span>
    </span>
  );
}

export function PageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="grid min-h-[45vh] place-items-center">
      <Spinner label={label} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Empty & error states — a shared frame so absence and failure read consistently
// ---------------------------------------------------------------------------

function StateFrame({
  icon,
  title,
  description,
  action,
  tone = 'neutral',
}: {
  icon: React.ReactNode;
  title: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
  tone?: 'neutral' | 'danger';
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
      className="mx-auto flex max-w-md flex-col items-center gap-4 px-6 py-14 text-center"
    >
      <div
        className={cn(
          'grid size-14 place-items-center rounded-full border [&_svg]:size-6',
          tone === 'danger'
            ? 'border-danger/25 bg-danger-soft text-danger'
            : 'border-line-strong bg-white/[0.04] text-content-muted',
        )}
      >
        {icon}
      </div>
      <div className="flex flex-col gap-1.5">
        <h3 className="text-h3">{title}</h3>
        {description && <p className="text-body text-content-secondary">{description}</p>}
      </div>
      {action && <div className="mt-1">{action}</div>}
    </motion.div>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: React.ReactNode;
  title: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <StateFrame icon={icon ?? <Inbox />} title={title} description={description} action={action} />
  );
}

/**
 * Renders any thrown value. An `ApiError` carries the backend's own message and correlation id;
 * the correlation id is surfaced because it is what makes a support conversation tractable.
 */
export function ErrorState({
  error,
  title,
  onRetry,
  retryLabel = 'Try again',
}: {
  error: unknown;
  title?: string;
  onRetry?: () => void;
  retryLabel?: string;
}) {
  const apiError = error instanceof ApiError ? error : null;
  const offline = apiError?.isNetworkError ?? false;

  return (
    <StateFrame
      tone="danger"
      icon={offline ? <WifiOff /> : <AlertTriangle />}
      title={title ?? (offline ? "Can't reach RoadScanner" : 'Something went wrong')}
      description={
        <>
          {apiError?.message ?? (error instanceof Error ? error.message : 'An unexpected error occurred.')}
          {apiError?.correlationId && (
            <span className="mt-2 block font-mono text-micro text-content-muted">
              Reference {apiError.correlationId}
            </span>
          )}
        </>
      }
      action={
        onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry}>
            <RefreshCw />
            {retryLabel}
          </Button>
        )
      }
    />
  );
}
