import * as React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MappingForm } from './mapping-form';
import { ApiError } from '@/lib/api/client';
import type { ProviderMapping } from '../types';

/**
 * The form's contract with the backend:
 *
 *   - the canonical location and provider are fixed once a mapping exists
 *   - blank optional fields are sent as null, never as ""
 *   - opaque provider metadata survives a full-replace edit
 *   - a 409's field name lands on that field, not in a generic banner
 */

const searchLocations = vi.fn();
const listProviders = vi.fn();

vi.mock('../api', () => ({
  providerMappingsApi: {},
  locationsApi: { search: (...args: unknown[]) => searchLocations(...args) },
}));

vi.mock('@/features/providers/api', () => ({
  providersApi: { list: (...args: unknown[]) => listProviders(...args) },
}));

const MAPPING: ProviderMapping = {
  id: 'm-1',
  provider: 'FLIXBUS',
  locationId: 'loc-1',
  locationDisplayName: 'Hyderabad MGBS',
  locationCity: 'Hyderabad',
  providerCityId: '58291',
  providerStationId: 'station-1',
  providerStationName: 'MGBS',
  providerMetadata: '{"platform":"7"}',
  verified: true,
  lastSynced: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-04T10:00:00Z',
};

function renderForm(props: Partial<React.ComponentProps<typeof MappingForm>> = {}) {
  const onSubmit = vi.fn();
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MappingForm submitting={false} onSubmit={onSubmit} {...props} />
    </QueryClientProvider>,
  );

  return { onSubmit };
}

beforeEach(() => {
  searchLocations.mockReset().mockResolvedValue([]);
  listProviders.mockReset().mockResolvedValue([]);
});

describe('MappingForm — creating', () => {
  it('refuses to submit without a canonical location', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.type(screen.getByLabelText('Provider'), 'FLIXBUS');
    await user.type(screen.getByLabelText('Provider city id'), '58291');
    await user.click(screen.getByRole('button', { name: /create mapping/i }));

    expect(await screen.findByText(/choose a canonical location/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('refuses a mapping with neither a city nor a station id', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm({
      initialLocation: { id: 'loc-1', displayName: 'Hyderabad', city: 'Hyderabad', state: null, country: 'India' },
    });

    await user.type(screen.getByLabelText('Provider'), 'FLIXBUS');
    await user.type(screen.getByLabelText('Provider station name'), 'MGBS');
    await user.click(screen.getByRole('button', { name: /create mapping/i }));

    expect(await screen.findByText(/cannot be looked up/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('sends null for optional fields left blank', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm({
      initialLocation: { id: 'loc-1', displayName: 'Hyderabad', city: 'Hyderabad', state: null, country: 'India' },
      initialProvider: 'FLIXBUS',
    });

    await user.type(screen.getByLabelText('Provider city id'), '58291');
    await user.click(screen.getByRole('button', { name: /create mapping/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith({
      locationId: 'loc-1',
      provider: 'FLIXBUS',
      providerCityId: '58291',
      providerStationId: null,
      providerStationName: null,
      providerMetadata: null,
      verified: false,
    });
  });
});

describe('MappingForm — editing', () => {
  it('renders the canonical location as read-only text, with no picker to change it', () => {
    renderForm({ mapping: MAPPING });

    // The location and provider identify which mapping this is. Re-pointing one is a delete plus
    // a create, so neither is editable here.
    expect(screen.getByText('Hyderabad MGBS')).toBeInTheDocument();
    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.getByLabelText('Provider')).toBeDisabled();
  });

  it('omits the location and provider from the payload entirely', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm({ mapping: MAPPING });

    await user.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const body = onSubmit.mock.calls[0]![0];
    // Sending them would suggest they are honoured; the backend silently ignores both.
    expect(body).not.toHaveProperty('locationId');
    expect(body).not.toHaveProperty('provider');
  });

  it('preserves opaque provider metadata across a full replace', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm({ mapping: MAPPING });

    await user.clear(screen.getByLabelText('Provider station name'));
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    // PUT is a full replace: dropping the payload would discard data this console cannot rebuild.
    expect(onSubmit.mock.calls[0]![0].providerMetadata).toBe('{"platform":"7"}');
    expect(onSubmit.mock.calls[0]![0].providerStationName).toBeNull();
  });

  it('clears a field that is emptied, rather than leaving the old value', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm({ mapping: MAPPING });

    await user.clear(screen.getByLabelText('Provider station id'));
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0]![0].providerStationId).toBeNull();
  });
});

describe('MappingForm — backend validation', () => {
  it('attaches a 409 to the field that caused it', async () => {
    renderForm({
      mapping: MAPPING,
      error: new ApiError({
        message: 'FLIXBUS city id 58291 is already mapped to a different location',
        status: 409,
        fieldErrors: [
          { field: 'providerCityId', message: 'FLIXBUS city id 58291 is already mapped to a different location' },
        ],
      }),
    });

    // The operator's only question at that moment is which field to change.
    expect(
      await screen.findByText(/already mapped to a different location/i),
    ).toBeInTheDocument();
  });

  it('shows an error with no field attached once, at the foot of the form', async () => {
    renderForm({
      mapping: MAPPING,
      error: new ApiError({
        message: 'Something went wrong on our side.',
        status: 500,
        correlationId: 'corr-123',
      }),
    });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong on our side.');
    expect(alert).toHaveTextContent('corr-123');
  });
});
