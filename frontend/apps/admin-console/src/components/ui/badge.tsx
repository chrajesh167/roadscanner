import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils/cn';

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 rounded-full border font-medium whitespace-nowrap [&_svg]:size-3',
  {
    variants: {
      tone: {
        neutral: 'bg-white/[0.05] border-line-strong text-content-secondary',
        accent: 'bg-accent-soft border-accent/25 text-accent-text',
        success: 'bg-success-soft border-success/25 text-success',
        warning: 'bg-warning-soft border-warning/25 text-warning',
        danger: 'bg-danger-soft border-danger/25 text-danger',
        info: 'bg-info-soft border-info/25 text-info',
      },
      size: {
        sm: 'px-2 py-0.5 text-micro uppercase tracking-[0.07em]',
        md: 'px-2.5 py-1 text-caption',
      },
    },
    defaultVariants: { tone: 'neutral', size: 'sm' },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, tone, size, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone, size }), className)} {...props} />;
}

/**
 * Maps a `HealthState` to a tone.
 *
 * `UNKNOWN` is neutral rather than a warning on purpose: a provider that has never been probed
 * has not failed, and colouring it as though it had would make a fresh environment look broken.
 * The health screen says "never probed" in words instead.
 */
export function healthTone(state: string): NonNullable<BadgeProps['tone']> {
  switch (state) {
    case 'HEALTHY':
      return 'success';
    case 'DEGRADED':
      return 'warning';
    case 'UNAVAILABLE':
      return 'danger';
    case 'UNKNOWN':
    default:
      return 'neutral';
  }
}

/** In service vs registered-but-off. Disabled is informational, never an error. */
export function enabledTone(enabled: boolean): NonNullable<BadgeProps['tone']> {
  return enabled ? 'success' : 'neutral';
}
