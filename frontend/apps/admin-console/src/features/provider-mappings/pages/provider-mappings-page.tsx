'use client';

import * as React from 'react';
import { Plus, Waypoints } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { EmptyState, ErrorState } from '@/components/ui/feedback';
import { PageContainer } from '@/components/layout/app-shell';
import { useDebouncedValue } from '@/lib/hooks/use-debounced-value';
import { MappingFilters } from '../components/mapping-filters';
import { MappingFormDialog } from '../components/mapping-form-dialog';
import { MappingPager } from '../components/mapping-pager';
import { MappingTable, MappingTableSkeleton } from '../components/mapping-table';
import { useDeleteMapping, useProviderMappings } from '../hooks/use-provider-mappings';
import { useMappingFilters } from '../hooks/use-mapping-filters';
import { UnmappedLocationsPanel } from './unmapped-locations-panel';
import type { ProviderMapping } from '../types';

/**
 * Location Mappings — the provider translation layer.
 *
 * <p>The screen an operator uses to answer "how does this provider refer to this place?", which is
 * the question that has to be settled before a provider can return anything useful for it. Two
 * scopes share one frame: the mappings that exist, and the canonical locations a provider still
 * cannot express.
 */
export function ProviderMappingsPage() {
  const { scope, provider, verified, search, page, size, setPage } = useMappingFilters();

  // Only what the server is asked about waits; the input itself stays instant.
  const debouncedSearch = useDebouncedValue(search, 300);

  const mappings = useProviderMappings({ provider, verified, search: debouncedSearch, page, size });
  const remove = useDeleteMapping();

  const [creating, setCreating] = React.useState(false);
  const [editing, setEditing] = React.useState<ProviderMapping | null>(null);
  const [pendingDelete, setPendingDelete] = React.useState<ProviderMapping | null>(null);

  const rows = mappings.data?.mappings ?? [];
  const filtered = Boolean(provider) || verified !== null || debouncedSearch.trim() !== '';

  return (
    <PageContainer
      title="Location mappings"
      description="How each canonical RoadScanner location is expressed in a provider’s own vocabulary. These are the only screens in the console that show a provider’s identifiers."
      actions={
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus />
          Create mapping
        </Button>
      }
    >
      <MappingFilters
        onRefresh={() => void mappings.refetch()}
        refreshing={mappings.isFetching}
        knownProviderCodes={rows.map((row) => row.provider)}
        searchPlaceholder={
          scope === 'mapped'
            ? 'Search locations, cities and provider identifiers…'
            : 'Search unmapped locations by name or city…'
        }
      />

      {scope === 'unmapped' ? (
        <UnmappedLocationsPanel />
      ) : mappings.isPending ? (
        <MappingTableSkeleton />
      ) : mappings.isError ? (
        <ErrorState
          error={mappings.error}
          title="Could not load the mappings"
          onRetry={() => void mappings.refetch()}
        />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={<Waypoints />}
          title={filtered ? 'No mapping matches those filters' : 'No mappings yet'}
          description={
            filtered
              ? 'Nothing in the translation layer matches. Widen the filters, or check the Unmapped tab for locations this provider cannot express yet.'
              : 'A mapping translates a location the catalogue already holds into one provider’s identifiers. Until one exists, that provider cannot be asked about the place.'
          }
          action={
            <Button onClick={() => setCreating(true)}>
              <Plus />
              Create mapping
            </Button>
          }
        />
      ) : (
        <>
          <MappingTable
            mappings={rows}
            onEdit={setEditing}
            onDelete={setPendingDelete}
            deletingId={pendingDelete?.id ?? null}
          />
          <MappingPager
            page={mappings.data.page}
            size={mappings.data.size}
            totalElements={mappings.data.totalElements}
            totalPages={mappings.data.totalPages}
            busy={mappings.isFetching}
            onPageChange={setPage}
          />
        </>
      )}

      <MappingFormDialog
        open={creating}
        onOpenChange={setCreating}
        initialProvider={provider ?? undefined}
      />

      <MappingFormDialog
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
        mapping={editing ?? undefined}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        destructive
        title="Delete this mapping?"
        confirmLabel="Delete mapping"
        loading={remove.isPending}
        description={
          pendingDelete && (
            <>
              <strong className="text-content">{pendingDelete.locationDisplayName}</strong> will no
              longer resolve to <strong className="text-content">{pendingDelete.provider}</strong>,
              and that provider will stop being asked about it. This is a hard delete — the
              translation stops existing rather than being retained as inactive. The canonical
              location itself is untouched.
            </>
          )
        }
        onConfirm={() => {
          if (!pendingDelete) return;
          const target = pendingDelete;
          setPendingDelete(null);
          remove.mutate(target);
        }}
      />
    </PageContainer>
  );
}
