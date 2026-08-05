'use client';

import { create } from 'zustand';

/**
 * Which half of the translation layer is on screen.
 *
 * <p>These are two different questions answered by two different endpoints, not one list with a
 * filter: `mapped` pages the mapping table, `unmapped` asks the worklist route which canonical
 * locations a provider still cannot express. They share a provider selection, which is the whole
 * reason this state is here rather than in each screen.
 */
export type MappingScope = 'mapped' | 'unmapped';

export interface MappingFilters {
  scope: MappingScope;
  /** Provider code, or null for "every provider". Required when scope is `unmapped`. */
  provider: string | null;
  /** null = both. */
  verified: boolean | null;
  search: string;
  page: number;
  size: number;
}

interface MappingFiltersStore extends MappingFilters {
  setScope: (scope: MappingScope) => void;
  setProvider: (provider: string | null) => void;
  setVerified: (verified: boolean | null) => void;
  setSearch: (search: string) => void;
  setPage: (page: number) => void;
  reset: () => void;
}

export const DEFAULT_MAPPING_FILTERS: MappingFilters = {
  scope: 'mapped',
  provider: null,
  verified: null,
  search: '',
  page: 0,
  size: 20,
};

/**
 * Filter state lives in a store rather than in the page because it outlives the page: switching
 * between the mapping table and the unmapped worklist, or opening a mapping and coming back,
 * should not silently drop the provider an operator selected — that is how someone edits the wrong
 * provider's row.
 *
 * <p>Every filter change resets `page` to 0. A page number is only meaningful against the result
 * set it was taken from; keeping it across a filter change lands the operator on an empty page 4
 * of a 2-page result and looks like data loss.
 */
export const useMappingFilters = create<MappingFiltersStore>((set) => ({
  ...DEFAULT_MAPPING_FILTERS,

  setScope: (scope) => set({ scope, page: 0 }),
  setProvider: (provider) => set({ provider, page: 0 }),
  setVerified: (verified) => set({ verified, page: 0 }),
  setSearch: (search) => set({ search, page: 0 }),
  setPage: (page) => set({ page }),
  reset: () => set(DEFAULT_MAPPING_FILTERS),
}));
