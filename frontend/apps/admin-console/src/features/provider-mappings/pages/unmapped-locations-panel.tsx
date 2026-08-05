'use client';

import * as React from 'react';
import { CheckCircle2, MapPin, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState } from '@/components/ui/feedback';
import { TBody, TD, TH, THead, TR, Table } from '@/components/ui/misc';
import { useDebouncedValue } from '@/lib/hooks/use-debounced-value';
import { MappingFormDialog } from '../components/mapping-form-dialog';
import { MappingTableSkeleton } from '../components/mapping-table';
import { useMappingFilters } from '../hooks/use-mapping-filters';
import { useUnmappedLocations } from '../hooks/use-provider-mappings';
import type { LocationSummary } from '../types';

/**
 * The onboarding worklist: canonical locations a given provider cannot yet express.
 *
 * <p>The complement of the mapping table, and the reason it is a worklist rather than a report is
 * the action on each row — an operator working down this list is *creating* the mappings that make
 * it shorter. Creating from a row pre-selects both the location and the provider, so the only
 * thing left to supply is what the provider's own console showed them.
 *
 * <p>A provider must be chosen first. "Unmapped for whom?" has no default answer, and the backend
 * requires the parameter, so the panel prompts rather than guessing.
 */
export function UnmappedLocationsPanel() {
  const { provider, search } = useMappingFilters();
  const debouncedSearch = useDebouncedValue(search, 300);

  const unmapped = useUnmappedLocations(provider, debouncedSearch);
  const [target, setTarget] = React.useState<LocationSummary | null>(null);

  if (!provider) {
    return (
      <EmptyState
        icon={<MapPin />}
        title="Choose a provider first"
        description="This list is per provider — a location mapped for one provider is still unmapped for another. Pick one above to see what it cannot express yet."
      />
    );
  }

  if (unmapped.isPending) return <MappingTableSkeleton rows={5} />;

  if (unmapped.isError) {
    return (
      <ErrorState
        error={unmapped.error}
        title="Could not load the worklist"
        onRetry={() => void unmapped.refetch()}
      />
    );
  }

  if (unmapped.data.length === 0) {
    return (
      <EmptyState
        icon={<CheckCircle2 />}
        title={
          debouncedSearch.trim()
            ? 'No unmapped location matches that'
            : `Every location is mapped for ${provider}`
        }
        description={
          debouncedSearch.trim()
            ? 'Clear the search to see the whole worklist for this provider.'
            : 'Nothing in the active catalogue is missing a mapping for this provider. New catalogue entries will appear here as they are added.'
        }
      />
    );
  }

  return (
    <>
      <Card padding="none" className="overflow-hidden">
        <Table>
          <THead>
            <TR>
              <TH scope="col">RoadScanner location</TH>
              <TH scope="col">City</TH>
              <TH scope="col" className="hidden sm:table-cell">
                Region
              </TH>
              <TH scope="col">
                <span className="sr-only">Actions</span>
              </TH>
            </TR>
          </THead>
          <TBody>
            {unmapped.data.map((location) => (
              <TR key={location.id} className="transition-colors hover:bg-white/[0.02]">
                <TD>
                  <span className="font-medium text-content">{location.displayName}</span>
                </TD>
                <TD>{location.city}</TD>
                <TD className="hidden sm:table-cell text-caption">
                  {[location.state, location.country].filter(Boolean).join(', ')}
                </TD>
                <TD>
                  <div className="flex justify-end">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setTarget(location)}
                      aria-label={`Create a ${provider} mapping for ${location.displayName}`}
                    >
                      <Plus />
                      Create mapping
                    </Button>
                  </div>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </Card>

      <p className="mt-4 text-caption text-content-muted">
        {unmapped.data.length} location{unmapped.data.length === 1 ? '' : 's'} with no {provider}{' '}
        mapping. Withdrawn locations are never listed — a soft-deleted place is not work to do.
      </p>

      <MappingFormDialog
        open={target !== null}
        onOpenChange={(open) => !open && setTarget(null)}
        initialLocation={target ?? undefined}
        initialProvider={provider}
      />
    </>
  );
}
