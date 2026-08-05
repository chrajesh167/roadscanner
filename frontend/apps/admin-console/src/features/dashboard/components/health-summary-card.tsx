'use client';

import Link from 'next/link';
import { Activity, CircleDashed } from 'lucide-react';
import { Badge, healthTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { humanizeEnum } from '@/lib/utils/format';
import type { HealthSummary } from '../summary';

/**
 * Health at a glance.
 *
 * <p>The empty state is the normal one. Health is probed on demand — the route performs a live
 * call to the provider — so a freshly opened console legitimately knows nothing, and this card
 * says so rather than implying everything is fine.
 */
export function HealthSummaryCard({ summary }: { summary: HealthSummary }) {
  const probed = summary.counts.HEALTHY + summary.counts.DEGRADED + summary.counts.UNAVAILABLE + summary.counts.UNKNOWN;

  return (
    <Card padding="md" className="flex flex-col">
      <CardHeader className="mb-5">
        <CardTitle>Provider health</CardTitle>
        <CardDescription>
          Results from probes run in this session. Nothing is polled automatically.
        </CardDescription>
      </CardHeader>

      {probed === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-6 text-center">
          <span
            className="grid size-11 place-items-center rounded-full border border-line-strong bg-white/[0.04] text-content-muted [&_svg]:size-5"
            aria-hidden
          >
            <CircleDashed />
          </span>
          <p className="text-caption text-content-secondary">
            {summary.notProbed} provider{summary.notProbed === 1 ? '' : 's'} not yet probed.
          </p>
          <Button asChild variant="secondary" size="sm">
            <Link href="/health">
              <Activity />
              Go to Health
            </Link>
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            {(['HEALTHY', 'DEGRADED', 'UNAVAILABLE', 'UNKNOWN'] as const)
              .filter((state) => summary.counts[state] > 0)
              .map((state) => (
                <Badge key={state} tone={healthTone(state)} size="md">
                  {summary.counts[state]} {humanizeEnum(state)}
                </Badge>
              ))}
            {summary.notProbed > 0 && (
              <Badge tone="neutral" size="md">
                {summary.notProbed} not probed
              </Badge>
            )}
          </div>

          {summary.failing.length > 0 && (
            <ul className="flex flex-col gap-2 border-t border-line pt-4">
              {summary.failing.map((provider) => (
                <li key={provider.code} className="flex items-center justify-between gap-3">
                  <span className="truncate text-caption text-content">{provider.displayName}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    {provider.consecutiveFailures > 0 && (
                      <span className="text-caption tabular-nums text-content-muted">
                        {provider.consecutiveFailures} consecutive
                      </span>
                    )}
                    <Badge tone={healthTone(provider.state)}>{provider.state}</Badge>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}
