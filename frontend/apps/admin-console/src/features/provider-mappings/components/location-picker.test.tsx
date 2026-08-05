import * as React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocationPicker } from './location-picker';
import type { LocationSummary } from '../types';

/**
 * The picker's one hard guarantee: it can find canonical locations and it can do nothing else.
 * A mapping translates a place the catalogue already holds — a control that could add one here
 * would invert the direction the catalogue is authored in.
 */

const searchLocations = vi.fn();

vi.mock('../api', () => ({
  providerMappingsApi: {},
  locationsApi: { search: (...args: unknown[]) => searchLocations(...args) },
}));

const HYDERABAD: LocationSummary = {
  id: 'loc-1',
  displayName: 'Hyderabad',
  city: 'Hyderabad',
  state: 'Telangana',
  country: 'India',
};

const SECUNDERABAD: LocationSummary = {
  id: 'loc-2',
  displayName: 'Secunderabad',
  city: 'Hyderabad',
  state: 'Telangana',
  country: 'India',
};

function renderPicker(value: LocationSummary | null = null) {
  const onChange = vi.fn();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <LocationPicker value={value} onChange={onChange} />
    </QueryClientProvider>,
  );

  return { onChange };
}

beforeEach(() => {
  searchLocations.mockReset().mockResolvedValue([HYDERABAD, SECUNDERABAD]);
});

describe('LocationPicker', () => {
  it('searches the catalogue and returns the chosen entry', async () => {
    const user = userEvent.setup();
    const { onChange } = renderPicker();

    await user.type(screen.getByRole('combobox'), 'hyd');

    // Queried positionally: every option's accessible name carries the same city line, so the
    // catalogue's own ordering is what distinguishes them.
    const options = await screen.findAllByRole('option');
    await user.click(options[0]!);

    expect(onChange).toHaveBeenCalledWith(HYDERABAD);
  });

  it('does not query on a single character', async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.type(screen.getByRole('combobox'), 'h');

    expect(await screen.findByText(/at least two characters/i)).toBeInTheDocument();
    // A one-letter prefix returns an arbitrary slice of the catalogue, which helps no one.
    expect(searchLocations).not.toHaveBeenCalled();
  });

  it('debounces, so a typed word is one query rather than one per keystroke', async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.type(screen.getByRole('combobox'), 'hyderabad');

    await waitFor(() => expect(searchLocations).toHaveBeenLastCalledWith('hyderabad'));

    // Eight of those keystrokes are search-eligible prefixes ("hy" through "hyderabad"). Without
    // debouncing every one is a request and a cache entry, and the answers can land out of order.
    expect(searchLocations.mock.calls.length).toBeLessThan(4);
  });

  it('offers no way to create a location when nothing matches', async () => {
    searchLocations.mockResolvedValue([]);
    const user = userEvent.setup();
    renderPicker();

    await user.type(screen.getByRole('combobox'), 'atlantis');

    expect(await screen.findByText(/no catalogue entry matches/i)).toBeInTheDocument();
    expect(screen.getByText(/mappings never create one/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /create|add|new location/i })).toBeNull();
  });

  it('is navigable by keyboard alone', async () => {
    const user = userEvent.setup();
    const { onChange } = renderPicker();

    await user.type(screen.getByRole('combobox'), 'hyd');
    await screen.findAllByRole('option');

    await user.keyboard('{ArrowDown}');
    await user.keyboard('{Enter}');

    expect(onChange).toHaveBeenCalledWith(SECUNDERABAD);
  });

  it('shows the selection instead of the search box once something is chosen', () => {
    renderPicker(HYDERABAD);

    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.getByText('Hyderabad')).toBeInTheDocument();
    expect(screen.getByText(/Telangana/)).toBeInTheDocument();
  });

  it('can clear the selection to search again', async () => {
    const user = userEvent.setup();
    const { onChange } = renderPicker(HYDERABAD);

    await user.click(screen.getByRole('button', { name: /clear hyderabad/i }));

    expect(onChange).toHaveBeenCalledWith(null);
  });
});
