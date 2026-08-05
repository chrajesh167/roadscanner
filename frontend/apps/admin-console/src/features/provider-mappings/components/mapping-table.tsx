'use client';

import { Pencil, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/feedback';
import { TBody, TD, TH, THead, TR, Table, Tooltip } from '@/components/ui/misc';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';
import type { ProviderMapping } from '../types';

/**
 * The mapping table.
 *
 * <p>Both halves of every translation are on screen at once — the RoadScanner location and the
 * provider's own identifiers — because that pairing *is* the thing being reviewed. A table of UUID
 * pairs is not something anyone can check by eye, which is why the backend resolves the location
 * alongside each row rather than leaving the console to look up twenty of them.
 *
 * <p>Provider identifiers are monospaced and truncated with the full value in a tooltip and in
 * `title`: they are long, opaque, and compared character by character when something is wrong.
 */
export function MappingTable({
  mappings,
  onEdit,
  onDelete,
  deletingId,
}: {
  mappings: ProviderMapping[];
  onEdit: (mapping: ProviderMapping) => void;
  onDelete: (mapping: ProviderMapping) => void;
  /** The row awaiting confirmation or mid-delete, dimmed while it settles. */
  deletingId?: string | null;
}) {
  return (
    <Card padding="none" className="overflow-hidden">
      <Table>
        <THead>
          <TR>
            <TH scope="col">RoadScanner location</TH>
            <TH scope="col" className="hidden sm:table-cell">
              City
            </TH>
            <TH scope="col">Provider</TH>
            <TH scope="col" className="hidden lg:table-cell">
              Provider city id
            </TH>
            <TH scope="col" className="hidden lg:table-cell">
              Provider station id
            </TH>
            <TH scope="col" className="hidden xl:table-cell">
              Station name
            </TH>
            <TH scope="col">Verified</TH>
            <TH scope="col" className="hidden md:table-cell">
              Updated
            </TH>
            <TH scope="col">
              <span className="sr-only">Actions</span>
            </TH>
          </TR>
        </THead>

        <TBody>
          {mappings.map((mapping) => (
            <TR
              key={mapping.id}
              className={cn(
                'transition-colors hover:bg-white/[0.02]',
                deletingId === mapping.id && 'opacity-50',
              )}
            >
              <TD>
                <span className="flex flex-col gap-0.5">
                  <span className="font-medium text-content">{mapping.locationDisplayName}</span>
                  <span className="text-caption text-content-muted sm:hidden">
                    {mapping.locationCity}
                  </span>
                </span>
              </TD>

              <TD className="hidden sm:table-cell">{mapping.locationCity}</TD>

              <TD>
                <Badge tone="accent">{mapping.provider}</Badge>
              </TD>

              <TD className="hidden lg:table-cell">
                <ProviderIdentifier value={mapping.providerCityId} />
              </TD>

              <TD className="hidden lg:table-cell">
                <ProviderIdentifier value={mapping.providerStationId} />
              </TD>

              <TD className="hidden xl:table-cell">
                {mapping.providerStationName ?? <Absent />}
              </TD>

              <TD>
                <Badge tone={mapping.verified ? 'success' : 'warning'}>
                  {mapping.verified ? 'Verified' : 'Unverified'}
                </Badge>
              </TD>

              <TD className="hidden whitespace-nowrap text-caption md:table-cell">
                {formatRelative(mapping.updatedAt)}
              </TD>

              <TD>
                <div className="flex items-center justify-end gap-1">
                  <RowAction
                    label={`Edit the ${mapping.provider} mapping for ${mapping.locationDisplayName}`}
                    onClick={() => onEdit(mapping)}
                  >
                    <Pencil className="size-4" />
                  </RowAction>
                  <RowAction
                    destructive
                    label={`Delete the ${mapping.provider} mapping for ${mapping.locationDisplayName}`}
                    onClick={() => onDelete(mapping)}
                  >
                    <Trash2 className="size-4" />
                  </RowAction>
                </div>
              </TD>
            </TR>
          ))}
        </TBody>
      </Table>
    </Card>
  );
}

function ProviderIdentifier({ value }: { value: string | null }) {
  if (!value) return <Absent />;

  return (
    <Tooltip content={<span className="font-mono break-all">{value}</span>}>
      <span
        title={value}
        className="block max-w-40 truncate font-mono text-caption text-content-secondary"
      >
        {value}
      </span>
    </Tooltip>
  );
}

/** Not every provider models both a city and a station — absence here is normal, not missing data. */
function Absent() {
  return (
    <span className="text-content-muted" aria-label="Not set">
      —
    </span>
  );
}

function RowAction({
  label,
  onClick,
  destructive = false,
  children,
}: {
  label: string;
  onClick: () => void;
  destructive?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={cn(
        'grid size-8 place-items-center rounded-sm transition-colors',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent',
        destructive
          ? 'text-content-muted hover:bg-danger-soft hover:text-danger'
          : 'text-content-muted hover:bg-white/[0.07] hover:text-content',
      )}
    >
      {children}
    </button>
  );
}

/** Matches the real table's row rhythm so nothing jumps when data lands. */
export function MappingTableSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <Card padding="none" className="overflow-hidden">
      <div className="flex flex-col divide-y divide-line">
        {Array.from({ length: rows }).map((_, index) => (
          <div key={index} className="flex items-center gap-4 px-4 py-4">
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-4 w-48" />
              <Skeleton className="h-3 w-24" />
            </div>
            <Skeleton className="hidden h-6 w-24 sm:block" />
            <Skeleton className="hidden h-4 w-36 lg:block" />
            <Skeleton className="h-6 w-20" />
            <Skeleton className="hidden h-4 w-20 md:block" />
            <Skeleton className="size-8" />
          </div>
        ))}
      </div>
    </Card>
  );
}
