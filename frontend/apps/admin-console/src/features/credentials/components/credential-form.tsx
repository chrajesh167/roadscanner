'use client';

import * as React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { ConfirmDialog, useConfirm } from '@/components/ui/confirm-dialog';
import { ApiError } from '@/lib/api/client';
import { useStoreCredentials } from '../hooks';
import {
  CREDENTIALS_FORM_DEFAULTS,
  credentialsFormSchema,
  toCredentialsRequest,
  type CredentialsFormValues,
} from '../schema';

/**
 * Replaces a provider's partner credentials.
 *
 * <p>Three rules hold this component together, and all three are about the same thing — a secret
 * typed here must not survive anywhere it can be read back:
 *
 * <ol>
 *   <li><strong>Secret inputs are always `type="password"`.</strong> There is no reveal toggle.
 *       The value cannot be re-read from the server, so an admin who is unsure what is stored must
 *       replace it rather than inspect it — and a shoulder-surfable partner secret buys nothing in
 *       exchange for that risk.</li>
 *   <li><strong>The form resets on success.</strong> Leaving the typed secret in component state
 *       after the write keeps it in memory, in React DevTools and in any future error report for
 *       as long as the screen stays open.</li>
 *   <li><strong>Nothing here is ever cached.</strong> The mutation stores only the returned
 *       summary — presence flags and a timestamp.</li>
 * </ol>
 *
 * <p>Autocomplete is off on every field: a browser password manager offering to save a partner
 * credential would put it in a second store nobody is auditing.
 */
export function CredentialForm({
  providerId,
  providerName,
  hasExisting,
}: {
  providerId: string;
  providerName: string;
  hasExisting: boolean;
}) {
  const store = useStoreCredentials(providerId);
  const confirm = useConfirm();

  const form = useForm<CredentialsFormValues>({
    resolver: zodResolver(credentialsFormSchema),
    defaultValues: CREDENTIALS_FORM_DEFAULTS,
  });

  const apiError = store.error instanceof ApiError ? store.error : null;

  const submit = () => {
    const values = form.getValues();
    store.mutate(toCredentialsRequest(values), {
      onSuccess: () => {
        // Rule 2. Clears the typed secrets from component state the moment they are no longer
        // needed, so nothing reachable from the DOM or a devtools snapshot still holds them.
        form.reset(CREDENTIALS_FORM_DEFAULTS);
        confirm.close();
      },
      onError: () => confirm.close(),
    });
  };

  // Replacing an existing secret is destructive — the previous one is not recoverable — so it is
  // confirmed. Storing the first set is not.
  const onSubmit = form.handleSubmit(() => {
    if (hasExisting) {
      confirm.ask();
    } else {
      submit();
    }
  });

  return (
    <>
      <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate autoComplete="off">
        <Field
          label="Partner email"
          htmlFor="partnerEmail"
          error={form.formState.errors.partnerEmail?.message}
          hint="Optional — only if this provider authenticates with an account."
        >
          <Input
            id="partnerEmail"
            type="email"
            autoComplete="off"
            spellCheck={false}
            invalid={Boolean(form.formState.errors.partnerEmail)}
            {...form.register('partnerEmail')}
          />
        </Field>

        <Field
          label="Partner password"
          htmlFor="partnerPassword"
          error={form.formState.errors.partnerPassword?.message}
          hint="Write-only. No endpoint returns this value once stored."
        >
          <Input
            id="partnerPassword"
            type="password"
            autoComplete="new-password"
            spellCheck={false}
            data-testid="partner-password"
            invalid={Boolean(form.formState.errors.partnerPassword)}
            {...form.register('partnerPassword')}
          />
        </Field>

        <Field
          label="Partner token"
          htmlFor="partnerToken"
          error={form.formState.errors.partnerToken?.message}
          hint="Write-only. Supply instead of, or alongside, a password."
        >
          <Input
            id="partnerToken"
            type="password"
            autoComplete="off"
            spellCheck={false}
            data-testid="partner-token"
            invalid={Boolean(form.formState.errors.partnerToken)}
            {...form.register('partnerToken')}
          />
        </Field>

        <p className="rounded-md border border-line bg-elevated px-4 py-3 text-caption text-content-secondary">
          This is a full replacement, not a patch. Any field left blank is cleared — send every
          secret this provider needs, together.
        </p>

        {apiError && (
          <div
            role="alert"
            className="rounded-md border border-danger/25 bg-danger-soft px-4 py-3 text-caption text-danger"
          >
            {apiError.message}
            {apiError.correlationId && (
              <span className="mt-1 block font-mono text-micro opacity-80">
                Reference {apiError.correlationId}
              </span>
            )}
          </div>
        )}

        <div className="flex justify-end">
          <Button type="submit" loading={store.isPending} loadingText="Storing…">
            <KeyRound />
            {hasExisting ? 'Replace credentials' : 'Store credentials'}
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={confirm.open}
        onOpenChange={confirm.setOpen}
        destructive
        title={`Replace ${providerName}'s credentials?`}
        description={
          <>
            The credentials stored today are overwritten and cannot be recovered — nothing in the
            platform can read them back. If the new secrets are wrong, this provider stops
            authenticating until you replace them again.
          </>
        }
        confirmLabel="Replace credentials"
        loading={store.isPending}
        onConfirm={submit}
      />
    </>
  );
}
