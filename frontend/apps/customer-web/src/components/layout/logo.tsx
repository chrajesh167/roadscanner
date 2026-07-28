import { cn } from '@/lib/utils/cn';

/** The mark: a road converging to a point, drawn as two tapering strokes. */
export function Logo({ className, showWordmark = true }: { className?: string; showWordmark?: boolean }) {
  return (
    <span className={cn('flex items-center gap-2.5', className)}>
      <svg
        width="26"
        height="26"
        viewBox="0 0 26 26"
        fill="none"
        aria-hidden
        className="shrink-0"
      >
        <rect width="26" height="26" rx="7" fill="url(#rs-bg)" />
        <path d="M8.6 19.5 11.4 6.5h1.9L11.6 19.5H8.6Z" fill="white" fillOpacity="0.92" />
        <path d="M14.1 19.5 15.4 6.5h1.9l-.6 13H14.1Z" fill="white" fillOpacity="0.55" />
        <defs>
          <linearGradient id="rs-bg" x1="0" y1="0" x2="26" y2="26" gradientUnits="userSpaceOnUse">
            <stop stopColor="#8f73ff" />
            <stop offset="1" stopColor="#5b3fd6" />
          </linearGradient>
        </defs>
      </svg>
      {showWordmark && (
        <span className="text-[0.95rem] font-semibold tracking-[-0.02em] text-content">
          RoadScanner
        </span>
      )}
    </span>
  );
}
