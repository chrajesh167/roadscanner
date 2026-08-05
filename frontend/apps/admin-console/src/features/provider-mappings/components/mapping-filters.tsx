'use client';

import * as React from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { cn } from '@/lib/utils/cn';
import { useProviders } from '@/features/providers/hooks';
import { useMappingFilters, type MappingScope } from '../hooks/use-mapping-filters';

/** Sentinel for "no filter" — Radix Select cannot hold an empty-string value. */
const ANY = '__any__';

/**
 * The filter bar for both halves of the translation layer.
 *
 * <p>Mapped and unmapped are presented as two tabs rather than a third dropdown value, because
 * they are answered by two different endpoints returning two different shapes — a mapping row and
 * a bare location. Dressing that as one filter would promise a single table that quietly changes
 * its columns.
 *
 * <p>The provider list is the registry's, fetched from `provider-integration-service`. If it
 * cannot be reached the select degrades to the codes already present in the current result set
 * rather than disappearing: an operator filtering mappings should not be blocked by an unrelated
 * service being down.
 */
export function MappingFilters({
  onRefresh,
  refreshing,
  knownProviderCodes = [],
  searchPlaceholder = 'Search locations, cities and provider identifiers…',
}: {
  onRefresh: () => void;
  refreshing: boolean;
  knownProviderCodes?: string[];
  searchPlaceholder?: string;
}) {
  const { scope, provider, verified, search, setScope, setProvider, setVerified, setSearch } =
    useMappingFilters();

  const providers = useProviders();

  const providerCodes = React.useMemo(() => {
    const codes = new Set<string>(knownProviderCodes);
    for (const registered of providers.data ?? []) codes.add(registered.code);
    if (provider) codes.add(provider);
    return [...codes].sort();
  }, [knownProviderCodes, providers.data, provider]);

  return (
    <div className="mb-5 flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2" role="tablist" aria-label="Mapping scope">
        <ScopeTab current={scope} value="mapped" onSelect={setScope}>
          Mapped
        </ScopeTab>
        <ScopeTab current={scope} value="unmapped" onSelect={setScope}>
          Unmapped
        </ScopeTab>
      </div>

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        <div className="min-w-0 flex-1">
          <label htmlFor="mapping-search" className="sr-only">
            Search mappings
          </label>
          <Input
            id="mapping-search"
            type="search"
            icon={<Search />}
            placeholder={searchPlaceholder}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="w-full min-w-40 sm:w-44">
            <label htmlFor="mapping-provider" className="sr-only">
              Filter by provider
            </label>
            <Select
              value={provider ?? ANY}
              onValueChange={(next) => setProvider(next === ANY ? null : next)}
            >
              <SelectTrigger id="mapping-provider" aria-label="Filter by provider">
                <SelectValue placeholder="Every provider" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Every provider</SelectItem>
                {providerCodes.map((code) => (
                  <SelectItem key={code} value={code}>
                    {code}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {scope === 'mapped' && (
            <div className="w-full min-w-36 sm:w-40">
              <label htmlFor="mapping-verified" className="sr-only">
                Filter by verification
              </label>
              <Select
                value={verified === null ? ANY : String(verified)}
                onValueChange={(next) => setVerified(next === ANY ? null : next === 'true')}
              >
                <SelectTrigger id="mapping-verified" aria-label="Filter by verification">
                  <SelectValue placeholder="Any status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ANY}>Any status</SelectItem>
                  <SelectItem value="true">Verified</SelectItem>
                  <SelectItem value="false">Unverified</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}

          <Button
            variant="secondary"
            size="sm"
            onClick={onRefresh}
            loading={refreshing}
            loadingText="Refreshing…"
          >
            <RefreshCw />
            Refresh
          </Button>
        </div>
      </div>
    </div>
  );
}

function ScopeTab({
  current,
  value,
  onSelect,
  children,
}: {
  current: MappingScope;
  value: MappingScope;
  onSelect: (scope: MappingScope) => void;
  children: React.ReactNode;
}) {
  const active = current === value;

  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={() => onSelect(value)}
      className={cn(
        'rounded-md px-3.5 py-2 text-caption font-medium transition-colors',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent',
        active
          ? 'bg-accent-soft text-accent-text'
          : 'text-content-secondary hover:bg-white/[0.05] hover:text-content',
      )}
    >
      {children}
    </button>
  );
}
