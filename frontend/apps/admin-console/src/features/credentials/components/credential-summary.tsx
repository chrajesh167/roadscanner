'use client';

import { KeyRound, Lock, ShieldAlert, ShieldCheck, Ticket } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/misc';
import { formatDateTime, formatRelative } from '@/lib/utils/format';
import { cn } from '@/lib/utils/cn';
import type { ProviderCredentialsResponse } from '@/lib/api/types';

/**
 * Everything the platform will tell anyone about a stored credential: whether a password exists,
 * whether a token exists, whether they are encrypted at rest, and when they last changed.
 *
 * <p>No value, no length, no masked stand-in of the real thing. A row of dots sized to the actual
 * secret leaks its length, and `ProviderCredentialsResponse` deliberately has no field that could
 * carry the value in the first place — this component has nothing to render even if it wanted to.
 */
export function CredentialSummary({ summary }: { summary: ProviderCredentialsResponse | null }) {
  if (!summary) {
    return (
      <div className="flex items-start gap-3 rounded-md border border-line bg-elevated px-4 py-3.5">
        <span
          className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full border border-line-strong bg-white/[0.04] text-content-muted [&_svg]:size-4"
          aria-hidden
        >
          <KeyRound />
        </span>
        <div className="flex flex-col gap-0.5">
          <p className="text-body text-content">No credentials stored</p>
          <p className="text-caption text-content-muted">
            This provider cannot authenticate until a password or token is supplied.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <PresenceTile
          icon={<Lock />}
          label="Password"
          present={summary.hasPassword}
          presentLabel="Stored"
          absentLabel="Not set"
        />
        <PresenceTile
          icon={<Ticket />}
          label="Token"
          present={summary.hasToken}
          presentLabel="Stored"
          absentLabel="Not set"
        />
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line pt-4">
        {summary.encrypted ? (
          <Badge tone="success" size="md">
            <ShieldCheck />
            Encrypted at rest
          </Badge>
        ) : (
          <Tooltip content="Written before credential encryption shipped. Replacing these secrets encrypts them.">
            <Badge tone="warning" size="md">
              <ShieldAlert />
              Not encrypted at rest
            </Badge>
          </Tooltip>
        )}

        <Tooltip content={formatDateTime(summary.updatedAt)}>
          <span className="text-caption text-content-secondary">
            Last changed {formatRelative(summary.updatedAt)}
          </span>
        </Tooltip>
      </div>
    </div>
  );
}

function PresenceTile({
  icon,
  label,
  present,
  presentLabel,
  absentLabel,
}: {
  icon: React.ReactNode;
  label: string;
  present: boolean;
  presentLabel: string;
  absentLabel: string;
}) {
  return (
    <div
      className={cn(
        'flex items-center gap-3 rounded-md border px-4 py-3',
        present ? 'border-success/25 bg-success-soft' : 'border-line bg-elevated',
      )}
    >
      <span
        className={cn(
          'grid size-8 shrink-0 place-items-center rounded-full [&_svg]:size-4',
          present ? 'bg-success/15 text-success' : 'bg-white/[0.05] text-content-muted',
        )}
        aria-hidden
      >
        {icon}
      </span>
      <span className="flex flex-col leading-tight">
        <span className="text-micro uppercase tracking-[0.08em] text-content-muted">{label}</span>
        <span className={cn('text-body', present ? 'text-content' : 'text-content-muted')}>
          {present ? presentLabel : absentLabel}
        </span>
      </span>
    </div>
  );
}
