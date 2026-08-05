'use client';

import * as React from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { PageContainer } from '@/components/layout/app-shell';
import { MappingFilters } from '../components/mapping-filters';
import { useMappingFilters } from '../hooks/use-mapping-filters';
import { useUnmappedLocations } from '../hooks/use-provider-mappings';
import { useDebouncedValue } from '@/lib/hooks/use-debounced-value';
import { UnmappedLocationsPanel } from './unmapped-locations-panel';

/**
 * The worklist as a page of its own, for the case it is the whole task rather than a detour from
 * the mapping table — onboarding a new provider means working this list to empty.
 *
 * <p>It renders the same panel the Unmapped tab does, against the same filter store, so the
 * provider selected in one is the provider selected in the other. The scope is forced on entry:
 * arriving here directly and finding the mapped table would make the URL a lie.
 */
export function UnmappedLocationsPage() {
  const { provider, search, scope, setScope } = useMappingFilters();
  const debouncedSearch = useDebouncedValue(search, 300);
  const unmapped = useUnmappedLocations(provider, debouncedSearch);

  React.useEffect(() => {
    if (scope !== 'unmapped') setScope('unmapped');
  }, [scope, setScope]);

  return (
    <PageContainer
      title="Unmapped locations"
      description="Canonical locations a provider cannot express yet. Working this list to empty is what onboarding a provider means."
      actions={
        <Button asChild variant="secondary" size="sm">
          <Link href="/location-mappings">All mappings</Link>
        </Button>
      }
    >
      <MappingFilters
        onRefresh={() => void unmapped.refetch()}
        refreshing={unmapped.isFetching}
        searchPlaceholder="Search unmapped locations by name or city…"
      />

      <UnmappedLocationsPanel />
    </PageContainer>
  );
}
