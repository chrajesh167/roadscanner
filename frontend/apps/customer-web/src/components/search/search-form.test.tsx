import * as React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SearchForm } from './search-form';
import { searchEndpoints } from '@/lib/api/endpoints/search';

/**
 * Where the canonical location id is acquired, or lost.
 *
 * Provider search is keyed by canonical location id — a place name cannot reach a provider at all.
 * These tests pin that picking a place from the catalogue carries its id all the way into the
 * search URL, and that typing a name that was never picked does not invent one.
 */

const HYDERABAD_ID = '11a059ad-77fc-4b40-b8de-37f20ddb4fec';
const BENGALURU_ID = 'ae8a7ec6-f394-45b6-9aa1-ace2e5f079bb';

/**
 * A date the form will accept, computed per run rather than written down.
 *
 * SearchForm sets `min={today}` on the date input, so a literal date silently becomes invalid the
 * day after it is written: the browser blocks submit, the router is never called, and the failure
 * surfaces as "expected spy to be called" rather than as the expired fixture it actually is.
 */
const TOMORROW = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/search',
}));

vi.mock('@/lib/api/endpoints/search', () => ({
  searchEndpoints: {
    locations: vi.fn(),
    suggestions: vi.fn().mockResolvedValue([]),
    searchTrips: vi.fn(),
    getTrip: vi.fn(),
  },
}));

const locations = vi.mocked(searchEndpoints.locations);

function location(id: string, displayName: string, city: string, state: string) {
  return { id, displayName, city, state, country: 'India' };
}

function renderForm() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SearchForm />
    </QueryClientProvider>,
  );
}

/**
 * Types into a combobox and picks the offered row.
 *
 * Clicks the control inside the option rather than the option element itself: the row's handler
 * lives on the button that fills it, and a click on the wrapping `li` would not reach it — which
 * is what a real pointer lands on anyway.
 */
async function pickPlace(user: ReturnType<typeof userEvent.setup>, label: string, name: string) {
  await user.type(screen.getByLabelText(label), name.slice(0, 4));
  const option = await screen.findByRole('option', { name: new RegExp(name, 'i') });
  await user.click(within(option).getByRole('button'));
}

beforeEach(() => {
  vi.clearAllMocks();
  locations.mockImplementation(async (query: string) =>
    query.toLowerCase().startsWith('hyde')
      ? [location(HYDERABAD_ID, 'Hyderabad', 'Hyderabad', 'Telangana')]
      : query.toLowerCase().startsWith('beng')
        ? [location(BENGALURU_ID, 'Bengaluru', 'Bengaluru', 'Karnataka')]
        : [],
  );
});

describe('SearchForm — canonical place selection', () => {
  it('carries both canonical ids into the search URL when places are picked', async () => {
    const user = userEvent.setup();
    renderForm();

    await pickPlace(user, 'From', 'Hyderabad');
    await pickPlace(user, 'To', 'Bengaluru');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => expect(push).toHaveBeenCalled());
    const url = new URL(push.mock.calls[0]![0], 'http://localhost');
    expect(url.searchParams.get('origin')).toBe('Hyderabad');
    expect(url.searchParams.get('destination')).toBe('Bengaluru');
    expect(url.searchParams.get('originLocationId')).toBe(HYDERABAD_ID);
    expect(url.searchParams.get('destinationLocationId')).toBe(BENGALURU_ID);
  });

  it('searches without ids when a place was typed but never picked', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText('From'), 'Hyderabad');
    await user.type(screen.getByLabelText('To'), 'Bengaluru');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    // Still a valid search over the index — the id is absent rather than guessed, because deriving
    // it from the typed text is how a traveller ends up on a bus to the wrong city.
    await waitFor(() => expect(push).toHaveBeenCalled());
    const url = new URL(push.mock.calls[0]![0], 'http://localhost');
    expect(url.searchParams.get('origin')).toBe('Hyderabad');
    expect(url.searchParams.has('originLocationId')).toBe(false);
    expect(url.searchParams.has('destinationLocationId')).toBe(false);
  });

  it('drops the id when a picked place is then edited', async () => {
    const user = userEvent.setup();
    renderForm();

    await pickPlace(user, 'From', 'Hyderabad');
    await pickPlace(user, 'To', 'Bengaluru');
    // The text no longer names the row that was chosen, so the id must not survive it.
    await user.type(screen.getByLabelText('From'), 'x');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => expect(push).toHaveBeenCalled());
    const url = new URL(push.mock.calls[0]![0], 'http://localhost');
    expect(url.searchParams.has('originLocationId')).toBe(false);
    expect(url.searchParams.get('destinationLocationId')).toBe(BENGALURU_ID);
  });

  it('swaps ids along with names so neither ends up on the wrong end of the route', async () => {
    const user = userEvent.setup();
    renderForm();

    await pickPlace(user, 'From', 'Hyderabad');
    await pickPlace(user, 'To', 'Bengaluru');
    await user.click(screen.getByRole('button', { name: /swap origin and destination/i }));
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => expect(push).toHaveBeenCalled());
    const url = new URL(push.mock.calls[0]![0], 'http://localhost');
    expect(url.searchParams.get('origin')).toBe('Bengaluru');
    expect(url.searchParams.get('originLocationId')).toBe(BENGALURU_ID);
    expect(url.searchParams.get('destination')).toBe('Hyderabad');
    expect(url.searchParams.get('destinationLocationId')).toBe(HYDERABAD_ID);
  });

  it('preserves ids the results page echoes back, so refining a search keeps federating', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <SearchForm
          variant="compact"
          defaultValues={{
            origin: { name: 'Hyderabad', id: HYDERABAD_ID },
            destination: { name: 'Bengaluru', id: BENGALURU_ID },
            date: TOMORROW,
          }}
        />
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => expect(push).toHaveBeenCalled());
    const url = new URL(push.mock.calls[0]![0], 'http://localhost');
    expect(url.searchParams.get('originLocationId')).toBe(HYDERABAD_ID);
    expect(url.searchParams.get('destinationLocationId')).toBe(BENGALURU_ID);
  });
});
