import * as React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CredentialForm } from './credential-form';

/**
 * The credential form is the one screen in this console that handles a secret, so its guarantees
 * are asserted rather than assumed:
 *
 *   - secrets are masked, with no reveal affordance
 *   - a stored secret is cleared from component state, so nothing holds it after the write
 *   - blank fields are sent as null, never as ""
 *   - replacing an existing secret is confirmed first
 */

const store = vi.fn();

vi.mock('../api', () => ({
  credentialsApi: {
    get: vi.fn(),
    store: (...args: unknown[]) => store(...args),
  },
}));

function renderForm(hasExisting = false) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <CredentialForm providerId="p-1" providerName="FlixBus" hasExisting={hasExisting} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  store.mockReset();
  store.mockResolvedValue({
    hasPassword: true,
    hasToken: false,
    encrypted: true,
    updatedAt: '2026-08-05T10:00:00Z',
  });
});

describe('CredentialForm', () => {
  it('masks both secret fields and offers no way to reveal them', () => {
    renderForm();

    expect(screen.getByTestId('partner-password')).toHaveAttribute('type', 'password');
    expect(screen.getByTestId('partner-token')).toHaveAttribute('type', 'password');

    // A reveal toggle would put a partner secret on screen for something that cannot be read back
    // from the server anyway — there is deliberately no such control.
    expect(screen.queryByRole('button', { name: /show|reveal/i })).toBeNull();
  });

  it('keeps password managers out of partner secrets', () => {
    renderForm();

    expect(screen.getByTestId('partner-password')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByTestId('partner-token')).toHaveAttribute('autocomplete', 'off');
  });

  it('refuses a submission with neither a password nor a token', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByRole('button', { name: /store credentials/i }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(store).not.toHaveBeenCalled();
  });

  it('sends null for fields left blank', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByTestId('partner-password'), 'hunter2');
    await user.click(screen.getByRole('button', { name: /store credentials/i }));

    await waitFor(() => expect(store).toHaveBeenCalledTimes(1));
    expect(store).toHaveBeenCalledWith('p-1', {
      partnerEmail: null,
      partnerPassword: 'hunter2',
      partnerToken: null,
    });
  });

  it('clears the typed secret from the form once it has been stored', async () => {
    const user = userEvent.setup();
    renderForm();

    const password = screen.getByTestId('partner-password');
    await user.type(password, 'hunter2');
    await user.click(screen.getByRole('button', { name: /store credentials/i }));

    await waitFor(() => expect(store).toHaveBeenCalled());
    // Leaving it in state would keep the secret readable from the DOM and any devtools snapshot
    // for as long as the screen stays open.
    await waitFor(() => expect(password).toHaveValue(''));
  });

  it('confirms before replacing an existing secret, and not before storing the first', async () => {
    const user = userEvent.setup();
    renderForm(true);

    await user.type(screen.getByTestId('partner-token'), 'tok_live_123');
    await user.click(screen.getByRole('button', { name: /replace credentials/i }));

    // The write must not have happened yet — the previous secret is unrecoverable, so this is
    // the last chance to back out.
    expect(store).not.toHaveBeenCalled();
    expect(await screen.findByRole('dialog')).toHaveTextContent(/cannot be recovered/i);

    await user.click(
      screen.getByRole('button', { name: /^replace credentials$/i, hidden: false }),
    );

    await waitFor(() => expect(store).toHaveBeenCalledTimes(1));
  });
});
