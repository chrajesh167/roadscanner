'use client';

import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip } from '@/components/ui/misc';
import { humanizeCapability } from '@/lib/utils/format';
import type { CapabilityCount } from '../summary';
import { cn } from '@/lib/utils/cn';

/**
 * Capability coverage across the registry.
 *
 * <p>Two numbers per row, not one: how many providers declare a capability, and how many of those
 * are actually in service. The gap between them is the useful signal — a capability declared only
 * by disabled providers is a capability the platform does not currently have.
 */
export function CapabilityBreakdown({
  counts,
  providerCount,
}: {
  counts: CapabilityCount[];
  providerCount: number;
}) {
  const widest = Math.max(1, ...counts.map((row) => row.total));

  return (
    <Card padding="md">
      <CardHeader className="mb-5">
        <CardTitle>Capability coverage</CardTitle>
        <CardDescription>
          What the registry can do, and how much of it is in service right now.
        </CardDescription>
      </CardHeader>

      <ul className="flex flex-col gap-3">
        {counts.map((row) => {
          const unsupported = row.total === 0;
          const noneEnabled = row.total > 0 && row.enabled === 0;

          return (
            <li key={row.capability} className="flex items-center gap-3">
              <span
                className={cn(
                  'w-44 shrink-0 truncate text-caption',
                  unsupported ? 'text-content-muted' : 'text-content-secondary',
                )}
              >
                {humanizeCapability(row.capability)}
              </span>

              <div
                className="relative h-2 flex-1 overflow-hidden rounded-full bg-white/[0.05]"
                role="img"
                aria-label={`${row.enabled} of ${row.total} providers declaring ${humanizeCapability(row.capability)} are enabled`}
              >
                {/* Declared, in the muted track colour; enabled, in accent on top of it. */}
                <div
                  className="absolute inset-y-0 left-0 rounded-full bg-white/[0.12]"
                  style={{ width: `${(row.total / widest) * 100}%` }}
                />
                <div
                  className="absolute inset-y-0 left-0 rounded-full bg-accent"
                  style={{ width: `${(row.enabled / widest) * 100}%` }}
                />
              </div>

              <Tooltip
                content={
                  unsupported
                    ? 'No registered provider declares this capability'
                    : `${row.enabled} enabled of ${row.total} declaring · ${providerCount} registered`
                }
              >
                <span
                  className={cn(
                    'w-14 shrink-0 text-right text-caption tabular-nums',
                    noneEnabled ? 'text-warning' : 'text-content',
                  )}
                >
                  {row.enabled}/{row.total}
                </span>
              </Tooltip>
            </li>
          );
        })}
      </ul>

      <p className="mt-5 text-caption text-content-muted">
        Left number: providers in service. Right: providers declaring it at all.
      </p>
    </Card>
  );
}
