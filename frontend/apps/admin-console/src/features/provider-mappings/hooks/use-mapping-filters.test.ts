import { act } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_MAPPING_FILTERS, useMappingFilters } from './use-mapping-filters';

beforeEach(() => {
  useMappingFilters.setState(DEFAULT_MAPPING_FILTERS);
});

describe('useMappingFilters', () => {
  it('starts unfiltered on the mapped scope', () => {
    const state = useMappingFilters.getState();
    expect(state.scope).toBe('mapped');
    expect(state.provider).toBeNull();
    expect(state.verified).toBeNull();
    expect(state.page).toBe(0);
  });

  it.each([
    ['provider', () => useMappingFilters.getState().setProvider('FLIXBUS')],
    ['verified', () => useMappingFilters.getState().setVerified(true)],
    ['search', () => useMappingFilters.getState().setSearch('mgbs')],
    ['scope', () => useMappingFilters.getState().setScope('unmapped')],
  ])('returns to the first page when %s changes', (_label, change) => {
    act(() => useMappingFilters.getState().setPage(3));
    expect(useMappingFilters.getState().page).toBe(3);

    act(change);

    // A page number only means something against the result set it was taken from. Keeping it
    // across a filter change lands the operator on an empty page and looks like data loss.
    expect(useMappingFilters.getState().page).toBe(0);
  });

  it('keeps the page when only the page changes', () => {
    act(() => useMappingFilters.getState().setPage(2));
    expect(useMappingFilters.getState().page).toBe(2);
  });

  it('shares the provider between scopes, so switching tabs does not lose it', () => {
    act(() => useMappingFilters.getState().setProvider('FLIXBUS'));
    act(() => useMappingFilters.getState().setScope('unmapped'));

    expect(useMappingFilters.getState().provider).toBe('FLIXBUS');
  });

  it('clears everything on reset', () => {
    act(() => {
      useMappingFilters.getState().setProvider('FLIXBUS');
      useMappingFilters.getState().setVerified(false);
      useMappingFilters.getState().setSearch('mgbs');
    });

    act(() => useMappingFilters.getState().reset());

    expect(useMappingFilters.getState()).toMatchObject(DEFAULT_MAPPING_FILTERS);
  });
});
