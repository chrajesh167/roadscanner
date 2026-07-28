'use client';

import * as React from 'react';
import { Timer } from 'lucide-react';
import { usePreferencesStore } from '@/lib/store/preferences-store';
import { formatCountdown } from '@/lib/utils/format';
import { cn } from '@/lib/utils/cn';

/**
 * Live countdown to `expiresAt` on a seat hold.
 *
 * The server owns expiry — this only reflects it. When it reaches zero the parent is told once via
 * `onExpire` so it can move the user somewhere sensible rather than letting them submit against a
 * hold the backend has already released.
 */
export function HoldTimer({
  expiresAt,
  onExpire,
  className,
}: {
  expiresAt: string;
  onExpire?: () => void;
  className?: string;
}) {
  const target = React.useMemo(() => new Date(expiresAt).getTime(), [expiresAt]);
  const [remaining, setRemaining] = React.useState(() => target - Date.now());
  const firedRef = React.useRef(false);

  React.useEffect(() => {
    firedRef.current = false;
    setRemaining(target - Date.now());

    const interval = setInterval(() => {
      const next = target - Date.now();
      setRemaining(next);
      if (next <= 0 && !firedRef.current) {
        firedRef.current = true;
        clearInterval(interval);
        onExpire?.();
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [target, onExpire]);

  const expired = remaining <= 0;
  // "Quiet timers" keeps the final minute visually calm; expiry is still shown either way, since
  // that is information the user needs rather than urgency theatre.
  const quiet = usePreferencesStore((state) => state.quietTimers);
  const urgent = !expired && !quiet && remaining < 60_000;

  return (
    <span
      role="timer"
      aria-live={urgent ? 'assertive' : 'off'}
      className={cn(
        'inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-caption tabular-nums transition-colors',
        expired
          ? 'border-danger/30 bg-danger-soft text-danger'
          : urgent
            ? 'border-warning/30 bg-warning-soft text-warning'
            : 'border-line-strong bg-white/[0.04] text-content-secondary',
        className,
      )}
    >
      <Timer className={cn('size-3.5', urgent && !expired && 'animate-pulse')} aria-hidden />
      {expired ? 'Hold expired' : `Seats held · ${formatCountdown(remaining)}`}
    </span>
  );
}
