import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils/cn';

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 rounded-full border font-medium whitespace-nowrap [&_svg]:size-3',
  {
    variants: {
      tone: {
        neutral: 'bg-white/[0.05] border-line-strong text-content-secondary',
        accent: 'bg-accent-soft border-accent/25 text-[#b9aaff]',
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
 * Maps a backend status enum to a tone. Booking and payment statuses are disjoint sets, so one
 * table serves both without collision.
 */
export function statusTone(status: string): NonNullable<BadgeProps['tone']> {
  switch (status) {
    case 'CONFIRMED':
    case 'CAPTURED':
    case 'COMPLETED':
    case 'AUTHORIZED':
      return 'success';
    case 'PENDING_PAYMENT':
    case 'PENDING':
    case 'CREATED':
    case 'REFUND_PENDING':
      return 'warning';
    case 'CANCELLED':
    case 'FAILED':
    case 'EXPIRED':
      return 'danger';
    case 'REFUNDED':
      return 'info';
    default:
      return 'neutral';
  }
}
