import * as React from 'react';
import type { LucideIcon } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/feedback';
import { cn } from '@/lib/utils/cn';

/**
 * One headline number with its label.
 *
 * <p>`tone` colours the icon only, never the number. A count is a fact, and colouring "2 disabled"
 * red would editorialise a state that is often entirely intentional — FlixBus ships disabled by
 * design.
 */
export function StatCard({
  label,
  value,
  hint,
  icon: Icon,
  tone = 'neutral',
}: {
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  icon: LucideIcon;
  tone?: 'neutral' | 'accent' | 'success' | 'warning' | 'danger';
}) {
  const toneClass = {
    neutral: 'border-line-strong bg-white/[0.04] text-content-muted',
    accent: 'border-accent/25 bg-accent-soft text-accent-text',
    success: 'border-success/25 bg-success-soft text-success',
    warning: 'border-warning/25 bg-warning-soft text-warning',
    danger: 'border-danger/25 bg-danger-soft text-danger',
  }[tone];

  return (
    <Card padding="md" className="flex items-start justify-between gap-4">
      <div className="flex min-w-0 flex-col gap-1.5">
        <p className="text-micro uppercase tracking-[0.08em] text-content-muted">{label}</p>
        <p className="text-h1 leading-none tabular-nums">{value}</p>
        {hint && <p className="text-caption text-content-secondary">{hint}</p>}
      </div>
      <span
        className={cn('grid size-10 shrink-0 place-items-center rounded-md border [&_svg]:size-4', toneClass)}
        aria-hidden
      >
        <Icon />
      </span>
    </Card>
  );
}

export function StatCardSkeleton() {
  return (
    <Card padding="md" className="flex items-start justify-between gap-4">
      <div className="flex w-full flex-col gap-2.5">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-8 w-14" />
        <Skeleton className="h-3 w-32" />
      </div>
      <Skeleton className="size-10 shrink-0 rounded-md" />
    </Card>
  );
}
