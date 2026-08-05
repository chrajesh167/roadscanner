import { cn } from '@/lib/utils/cn';

/**
 * The console's mark. Deliberately distinguished from customer-web's: an admin tool that looks
 * identical to the traveller-facing site is one tab-switch away from someone disabling a provider
 * while they think they are booking a seat.
 */
export function Logo({ className, compact = false }: { className?: string; compact?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <span
        className="grid size-8 shrink-0 place-items-center rounded-md bg-accent-soft border border-accent/30 text-caption font-semibold text-accent-text"
        aria-hidden
      >
        RS
      </span>
      {!compact && (
        <span className="flex flex-col leading-none">
          <span className="text-body font-semibold text-content">RoadScanner</span>
          <span className="text-micro uppercase tracking-[0.14em] text-content-muted">
            Admin Console
          </span>
        </span>
      )}
    </span>
  );
}
