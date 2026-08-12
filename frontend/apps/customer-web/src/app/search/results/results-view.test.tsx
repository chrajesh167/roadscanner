import * as React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ResultsView } from './results-view';
import { searchEndpoints } from '@/lib/api/endpoints/search';
import type {
  ProviderTripResponse,
  SearchResultResponse,
  TripResponse,
} from '@/lib/api/types';

/**
 * What a traveller actually ends up looking at.
 *
 * The assertions here are about the two things this screen has to get right and cannot verify
 * anywhere else: that a search which knows its canonical places actually asks for provider results,
 * and that a departure arriving twice — live and indexed — is shown once.
 */

const HYDERABAD_ID = '11a059ad-77fc-4b40-b8de-37f20ddb4fec';
const BENGALURU_ID = 'ae8a7ec6-f394-45b6-9aa1-ace2e5f079bb';
const CATALOG_TRIP_ID = 'ce7a9c5b-6503-459b-96fc-91620c4ac15e';

let searchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => searchParams,
  usePathname: () => '/search/results',
}));

vi.mock('@/lib/api/endpoints/search', () => ({
  searchEndpoints: {
    searchTrips: vi.fn(),
    locations: vi.fn().mockResolvedValue([]),
    suggestions: vi.fn().mockResolvedValue([]),
    getTrip: vi.fn(),
  },
}));

const searchTrips = vi.mocked(searchEndpoints.searchTrips);

function indexedTrip(overrides: Partial<TripResponse> = {}): TripResponse {
  return {
    tripId: CATALOG_TRIP_ID,
    operatorId: '00000000-0000-0000-0000-0000000000aa',
    operatorName: 'Mock Travels',
    origin: 'Hyderabad',
    destination: 'Bengaluru',
    departureTime: '2026-08-13T20:00:00Z',
    arrivalTime: '2026-08-14T02:00:00Z',
    durationMinutes: 360,
    busTypeCategory: 'AC Sleeper',
    amenities: ['WiFi'],
    fareAmount: '899.00',
    fareCurrency: 'INR',
    bookable: true,
    ratingAverage: 4.2,
    ratingReviewCount: 12,
    availableSeats: 29,
    availabilityKnown: true,
    ...overrides,
  };
}

function providerTrip(overrides: Partial<ProviderTripResponse> = {}): ProviderTripResponse {
  return {
    providerCode: 'MOCK',
    providerTripId: 'MOCK-HYDERABAD-BENGALURU-2026-08-13-AC-SLEEPER',
    operatorName: 'Mock Travels',
    origin: 'Hyderabad',
    destination: 'Bengaluru',
    departureTime: '2026-08-13T20:00:00Z',
    arrivalTime: '2026-08-14T02:00:00Z',
    serviceClass: 'AC Sleeper',
    fareAmount: '899.00',
    fareCurrency: 'INR',
    seatsAvailable: 29,
    boardingPointId: 'mock-point-Hyderabad',
    alightingPointId: 'mock-point-Bengaluru',
    catalogTripId: CATALOG_TRIP_ID,
    ...overrides,
  };
}

function response(overrides: Partial<SearchResultResponse> = {}): SearchResultResponse {
  return {
    content: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
    providerTrips: [],
    providerSearchComplete: true,
    ...overrides,
  };
}

function renderResults() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ResultsView />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  searchParams = new URLSearchParams({
    origin: 'Hyderabad',
    destination: 'Bengaluru',
    date: '2026-08-13',
    originLocationId: HYDERABAD_ID,
    destinationLocationId: BENGALURU_ID,
  });
  searchTrips.mockResolvedValue(response());
});

describe('ResultsView — the provider search request', () => {
  it('sends both canonical location ids so the backend federates', async () => {
    renderResults();

    await waitFor(() => expect(searchTrips).toHaveBeenCalled());
    expect(searchTrips.mock.calls[0]![0]).toMatchObject({
      origin: 'Hyderabad',
      destination: 'Bengaluru',
      date: '2026-08-13',
      originLocationId: HYDERABAD_ID,
      destinationLocationId: BENGALURU_ID,
    });
  });

  it('omits the ids when the places were typed rather than picked', async () => {
    // The backend federates only with both ids. Sending a guessed one would ask a provider about a
    // city nobody confirmed — the mistranslation the mapping rules exist to prevent.
    searchParams = new URLSearchParams({
      origin: 'Hyderabad',
      destination: 'Bengaluru',
      date: '2026-08-13',
    });

    renderResults();

    await waitFor(() => expect(searchTrips).toHaveBeenCalled());
    expect(searchTrips.mock.calls[0]![0].originLocationId).toBeUndefined();
    expect(searchTrips.mock.calls[0]![0].destinationLocationId).toBeUndefined();
  });
});

describe('ResultsView — rendering provider results', () => {
  it('renders a provider bus under its own provider name', async () => {
    searchTrips.mockResolvedValue(response({ providerTrips: [providerTrip()] }));

    renderResults();

    const section = await screen.findByRole('region', { name: /provider buses/i });
    expect(within(section).getByText('Mock Travels')).toBeInTheDocument();
    expect(within(section).getByText('MOCK')).toBeInTheDocument();
    expect(within(section).getByText('AC Sleeper')).toBeInTheDocument();
    // Never presented as another provider's inventory.
    expect(screen.queryByText(/flixbus/i)).toBeNull();
  });

  it('links a bookable provider trip to its catalog trip, not its provider trip id', async () => {
    searchTrips.mockResolvedValue(response({ providerTrips: [providerTrip()] }));

    renderResults();

    const link = await screen.findByRole('link', { name: /mock travels via mock/i });
    expect(link).toHaveAttribute('href', `/trips/${CATALOG_TRIP_ID}`);
  });

  it('shows an unimported provider trip without making it selectable', async () => {
    searchTrips.mockResolvedValue(
      response({ providerTrips: [providerTrip({ catalogTripId: null })] }),
    );

    renderResults();

    // Real departure, nothing to book against — a link here would lead to a seat map that cannot
    // exist.
    expect(await screen.findByText(/not yet bookable/i)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /mock travels via mock/i })).toBeNull();
  });

  it('states that the answer is partial when a provider failed', async () => {
    searchTrips.mockResolvedValue(
      response({ providerTrips: [providerTrip()], providerSearchComplete: false }),
    );

    renderResults();

    expect(
      await screen.findByText(/some providers are temporarily unavailable/i),
    ).toBeInTheDocument();
  });

  it('does not present a failed provider search as an empty route', async () => {
    searchTrips.mockResolvedValue(response({ providerSearchComplete: false }));

    renderResults();

    // An unreachable provider and a route with no buses produce the same empty list; reading one as
    // the other tells a traveller a route is sold out when nobody actually asked.
    expect(
      await screen.findByText(/some providers are temporarily unavailable/i),
    ).toBeInTheDocument();
  });

  it('still renders ordinary indexed trips', async () => {
    searchTrips.mockResolvedValue(
      response({ content: [indexedTrip()], totalElements: 1, totalPages: 1 }),
    );

    renderResults();

    expect(await screen.findByText('Mock Travels')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /provider buses/i })).toBeNull();
  });
});

describe('ResultsView — deduplication', () => {
  it('shows a departure once when it arrives both live and indexed', async () => {
    // Catalog sync imported this exact bus, so it is in both lists. Identity is the providerTripId,
    // expressed through the catalogTripId resolved from it.
    searchTrips.mockResolvedValue(
      response({
        content: [indexedTrip()],
        totalElements: 1,
        totalPages: 1,
        providerTrips: [providerTrip()],
      }),
    );

    renderResults();

    await screen.findByRole('region', { name: /provider buses/i });
    const links = screen.getAllByRole('link').filter((link) =>
      link.getAttribute('href')?.startsWith('/trips/'),
    );
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute('href', `/trips/${CATALOG_TRIP_ID}`);
  });

  it('keeps an indexed trip that no provider result claims', async () => {
    const otherTrip = indexedTrip({
      tripId: '5874187d-8aa5-4874-b7a2-6ede3221884e',
      operatorName: 'Other Travels',
    });
    searchTrips.mockResolvedValue(
      response({
        content: [indexedTrip(), otherTrip],
        totalElements: 2,
        totalPages: 1,
        providerTrips: [providerTrip()],
      }),
    );

    renderResults();

    // Only the twin is suppressed — dedupe must not swallow unrelated results.
    expect(await screen.findByText('Other Travels')).toBeInTheDocument();
    const links = screen.getAllByRole('link').filter((link) =>
      link.getAttribute('href')?.startsWith('/trips/'),
    );
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      `/trips/${CATALOG_TRIP_ID}`,
      `/trips/${otherTrip.tripId}`,
    ]);
  });

  it('does not suppress an indexed trip on the strength of a matching price and time alone', async () => {
    // A provider trip that resolved to no catalog trip is not evidence about any indexed row, even
    // one with identical operator, fare and schedule. Only the resolved identity counts.
    searchTrips.mockResolvedValue(
      response({
        content: [indexedTrip()],
        totalElements: 1,
        totalPages: 1,
        providerTrips: [providerTrip({ catalogTripId: null })],
      }),
    );

    renderResults();

    expect(
      await screen.findByRole('link', { name: new RegExp(`departs`, 'i') }),
    ).toHaveAttribute('href', `/trips/${CATALOG_TRIP_ID}`);
  });
});
