import * as React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProviderMappingsPage } from './provider-mappings-page';
import { TooltipProvider } from '@/components/ui/misc';
import { DEFAULT_MAPPING_FILTERS, useMappingFilters } from '../hooks/use-mapping-filters';
import type { ProviderMapping, ProviderMappingPage } from '../types';

/**
 * The list screen end to end against a stubbed API: rendering, debounced search, filtering,
 * pagination, and the delete path — including that the row leaves the table before the server has
 * answered and comes back if it refuses.
 */

const list = vi.fn();
const remove = vi.fn();
const unmappedLocations = vi.fn();
const listProviders = vi.fn();

vi.mock('../api', () => ({
  providerMappingsApi: {
    list: (...args: unknown[]) => list(...args),
    remove: (...args: unknown[]) => remove(...args),
    unmappedLocations: (...args: unknown[]) => unmappedLocations(...args),
    create: vi.fn(),
    update: vi.fn(),
    get: vi.fn(),
  },
  locationsApi: { search: vi.fn().mockResolvedValue([]) },
}));

vi.mock('@/features/providers/api', () => ({
  providersApi: { list: (...args: unknown[]) => listProviders(...args) },
}));

function mapping(overrides: Partial<ProviderMapping> = {}): ProviderMapping {
  return {
    id: 'm-1',
    provider: 'FLIXBUS',
    locationId: 'loc-1',
    locationDisplayName: 'Hyderabad MGBS',
    locationCity: 'Hyderabad',
    providerCityId: '58291',
    providerStationId: 'station-1',
    providerStationName: 'MGBS',
    providerMetadata: null,
    verified: true,
    lastSynced: null,
    createdAt: '2026-08-01T10:00:00Z',
    updatedAt: '2026-08-04T10:00:00Z',
    ...overrides,
  };
}

function page(mappings: ProviderMapping[], overrides: Partial<ProviderMappingPage> = {}): ProviderMappingPage {
  return {
    mappings,
    totalElements: mappings.length,
    totalPages: 1,
    page: 0,
    size: 20,
    ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <ProviderMappingsPage />
      </TooltipProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useMappingFilters.setState(DEFAULT_MAPPING_FILTERS);
  list.mockReset().mockResolvedValue(page([mapping()]));
  remove.mockReset().mockResolvedValue(undefined);
  unmappedLocations.mockReset().mockResolvedValue([]);
  listProviders.mockReset().mockResolvedValue([]);
});

describe('ProviderMappingsPage — the table', () => {
  it('shows both halves of each translation', async () => {
    renderPage();

    expect(await screen.findByText('Hyderabad MGBS')).toBeInTheDocument();

    // Both vocabularies in the same row is the point — a table of UUID pairs cannot be checked
    // by eye. Scoped to the row so the column headings cannot satisfy the assertion.
    const row = within(screen.getAllByRole('row')[1]!);
    // The city appears twice by design — once in its column, once stacked under the name for
    // narrow screens where that column is hidden.
    expect(row.getAllByText('Hyderabad').length).toBeGreaterThan(0);
    expect(row.getByText('58291')).toBeInTheDocument();
    expect(row.getByText('station-1')).toBeInTheDocument();
    expect(row.getByText('MGBS')).toBeInTheDocument();
    expect(row.getByText('FLIXBUS')).toBeInTheDocument();
    expect(row.getByText('Verified')).toBeInTheDocument();
  });

  it('distinguishes an unverified mapping', async () => {
    list.mockResolvedValue(page([mapping({ verified: false })]));
    renderPage();

    expect(await screen.findByText('Unverified')).toBeInTheDocument();
  });

  it('renders a skeleton before the first page lands, not an empty table', () => {
    list.mockReturnValue(new Promise(() => {}));
    const { container } = renderPage();

    expect(container.querySelectorAll('.shimmer').length).toBeGreaterThan(0);
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('offers an empty state that leads to creating one', async () => {
    list.mockResolvedValue(page([]));
    renderPage();

    expect(await screen.findByText(/no mappings yet/i)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /create mapping/i }).length).toBeGreaterThan(0);
  });

  it('says so when filters are what emptied the table', async () => {
    list.mockResolvedValue(page([]));
    useMappingFilters.setState({ provider: 'FLIXBUS' });
    renderPage();

    expect(await screen.findByText(/no mapping matches those filters/i)).toBeInTheDocument();
  });

  it('surfaces a failure with a way to retry', async () => {
    list.mockRejectedValue(new Error('boom'));
    renderPage();

    expect(await screen.findByText(/could not load the mappings/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });
});

describe('ProviderMappingsPage — search and filters', () => {
  it('debounces the search, then queries once for the settled term', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Hyderabad MGBS');

    await user.type(screen.getByLabelText(/search mappings/i), 'mgbs');

    await waitFor(() =>
      expect(list).toHaveBeenCalledWith(expect.objectContaining({ search: 'mgbs' })),
    );

    // One request for the word, not one per keystroke: no intermediate term is ever sent.
    const searched = list.mock.calls.map(([query]) => query.search);
    expect(searched).not.toContain('m');
    expect(searched).not.toContain('mg');
    expect(searched).not.toContain('mgb');
  });

  it('passes the provider and verification filters through to the query', async () => {
    renderPage();
    await screen.findByText('Hyderabad MGBS');

    useMappingFilters.getState().setProvider('FLIXBUS');
    useMappingFilters.getState().setVerified(false);

    await waitFor(() =>
      expect(list).toHaveBeenCalledWith(
        expect.objectContaining({ provider: 'FLIXBUS', verified: false }),
      ),
    );
  });

  it('refetches on demand', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Hyderabad MGBS');

    const before = list.mock.calls.length;
    await user.click(screen.getByRole('button', { name: /refresh/i }));

    await waitFor(() => expect(list.mock.calls.length).toBeGreaterThan(before));
  });

  it('switches to the unmapped worklist without losing the provider', async () => {
    const user = userEvent.setup();
    useMappingFilters.setState({ provider: 'FLIXBUS' });
    renderPage();
    await screen.findByText('Hyderabad MGBS');

    await user.click(screen.getByRole('tab', { name: /unmapped/i }));

    await waitFor(() => expect(unmappedLocations).toHaveBeenCalledWith('FLIXBUS', ''));
  });

  it('asks for a provider before showing a worklist that needs one', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Hyderabad MGBS');

    await user.click(screen.getByRole('tab', { name: /unmapped/i }));

    expect(await screen.findByText(/choose a provider first/i)).toBeInTheDocument();
    // Firing the request without one would trade a clear prompt for a 400.
    expect(unmappedLocations).not.toHaveBeenCalled();
  });
});

describe('ProviderMappingsPage — pagination', () => {
  it('reports the window and pages forward', async () => {
    const user = userEvent.setup();
    list.mockResolvedValue(page([mapping()], { totalElements: 45, totalPages: 3, page: 0 }));
    renderPage();
    await screen.findByText('Hyderabad MGBS');

    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /next/i }));

    await waitFor(() => expect(list).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })));
  });

  it('cannot page back from the first page', async () => {
    list.mockResolvedValue(page([mapping()], { totalElements: 45, totalPages: 3, page: 0 }));
    renderPage();
    await screen.findByText('Hyderabad MGBS');

    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled();
  });
});

describe('ProviderMappingsPage — delete', () => {
  /** Opens the confirmation for one row, addressed by the location it maps. */
  async function openDeleteConfirm(location = 'Hyderabad MGBS') {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(location);

    await user.click(
      screen.getByRole('button', {
        name: new RegExp(`delete the FLIXBUS mapping for ${location}`, 'i'),
      }),
    );
    return user;
  }

  it('confirms before deleting, naming what stops resolving', async () => {
    await openDeleteConfirm();

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/delete this mapping\?/i)).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/no longer resolve/i);
    // Nothing has happened yet — this is the last chance to back out of a hard delete.
    expect(remove).not.toHaveBeenCalled();
  });

  it('leaves the mapping alone when the confirmation is dismissed', async () => {
    const user = await openDeleteConfirm();

    await user.click(screen.getByRole('button', { name: /^cancel$/i }));

    expect(remove).not.toHaveBeenCalled();
    expect(screen.getByText('Hyderabad MGBS')).toBeInTheDocument();
  });

  it('removes the row optimistically, before the server has answered', async () => {
    let release: () => void = () => {};
    remove.mockReturnValue(new Promise<void>((resolve) => { release = () => resolve(); }));

    const user = await openDeleteConfirm();
    await user.click(screen.getByRole('button', { name: /delete mapping/i }));

    // The request is still in flight and the row is already gone.
    await waitFor(() => expect(screen.queryByText('Hyderabad MGBS')).toBeNull());
    expect(remove).toHaveBeenCalledWith('m-1');

    release();
  });

  it('puts the row back when the delete fails', async () => {
    remove.mockRejectedValue(new Error('nope'));

    const user = await openDeleteConfirm();
    await user.click(screen.getByRole('button', { name: /delete mapping/i }));

    await waitFor(() => expect(remove).toHaveBeenCalled());
    // The server's answer always wins: a failed delete must not leave a row missing from a table
    // the operator will go on to trust.
    expect(await screen.findByText('Hyderabad MGBS')).toBeInTheDocument();
  });

  it('keeps the total honest while the row is gone', async () => {
    let release: () => void = () => {};
    remove.mockReturnValue(new Promise<void>((resolve) => { release = () => resolve(); }));
    list.mockResolvedValue(page([mapping(), mapping({ id: 'm-2', locationDisplayName: 'Vijayawada' })], {
      totalElements: 2,
      totalPages: 1,
    }));

    const user = await openDeleteConfirm();
    await user.click(screen.getByRole('button', { name: /delete mapping/i }));

    // A stale total is how a pager offers a page that no longer exists.
    await waitFor(() =>
      expect(screen.getByTestId('mapping-pager-summary')).toHaveTextContent(/of\s*1\b/),
    );

    release();
  });
});
