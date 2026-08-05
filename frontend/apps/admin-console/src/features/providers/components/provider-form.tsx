'use client';

import * as React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { ApiError } from '@/lib/api/client';
import { cn } from '@/lib/utils/cn';
import { humanizeCapability } from '@/lib/utils/format';
import { PROVIDER_CAPABILITIES, SUGGESTED_PROVIDER_CATEGORIES } from '@/lib/api/types';
import type { ProviderCapability, ProviderRequest, ProviderResponse } from '@/lib/api/types';
import { PROVIDER_FORM_DEFAULTS, providerFormSchema, type ProviderFormValues } from '../schema';

/**
 * One form for both registering and editing, because the backend takes one shape for both
 * (`ProviderRequest`). The only difference is `code`: required on create, ignored on update — so
 * in edit mode it is rendered read-only rather than sent and silently discarded.
 *
 * <p>There is no "enabled" control here. The backend deliberately keeps enabling out of create
 * and update: a provider is registered disabled and put into service as a separate act, ideally
 * after its connection has been tested. Adding a checkbox would imply a capability the API does
 * not offer.
 */
export function ProviderForm({
  provider,
  onSubmit,
  submitting,
  error,
  onCancel,
}: {
  provider?: ProviderResponse;
  onSubmit: (body: ProviderRequest) => void;
  submitting: boolean;
  error?: unknown;
  onCancel?: () => void;
}) {
  const editing = provider !== undefined;

  const form = useForm<ProviderFormValues>({
    resolver: zodResolver(providerFormSchema),
    defaultValues: provider
      ? {
          code: provider.code,
          category: provider.category,
          displayName: provider.displayName,
          capabilities: provider.capabilities,
          baseUrl: provider.baseUrl ?? '',
          timeoutMs: provider.timeoutMs,
          retryCount: provider.retryCount,
        }
      : PROVIDER_FORM_DEFAULTS,
  });

  // Field-level failures from the backend are surfaced on the fields themselves; anything else
  // shows once at the foot of the form.
  const apiError = error instanceof ApiError ? error : null;
  React.useEffect(() => {
    if (!apiError) return;
    for (const fieldError of apiError.fieldErrors) {
      if (fieldError.field in providerFormSchema.shape) {
        form.setError(fieldError.field as keyof ProviderFormValues, {
          message: fieldError.message,
        });
      }
    }
  }, [apiError, form]);

  const submit = form.handleSubmit((values) => {
    onSubmit({
      // Omitted entirely when editing: the backend ignores it, and sending it would suggest
      // otherwise to anyone reading the request.
      ...(editing ? {} : { code: values.code }),
      category: values.category,
      displayName: values.displayName,
      capabilities: values.capabilities,
      baseUrl: values.baseUrl.trim() === '' ? null : values.baseUrl.trim(),
      timeoutMs: values.timeoutMs,
      retryCount: values.retryCount,
    });
  });

  return (
    <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <Field
          label="Code"
          htmlFor="code"
          error={form.formState.errors.code?.message}
          hint={
            editing
              ? 'Not editable — sessions and health records are keyed on it'
              : 'Unique, uppercase by convention (e.g. FLIXBUS)'
          }
        >
          <Input
            id="code"
            readOnly={editing}
            disabled={editing}
            className={cn(editing && 'font-mono')}
            invalid={Boolean(form.formState.errors.code)}
            {...form.register('code')}
          />
        </Field>

        <Field
          label="Category"
          htmlFor="category"
          error={form.formState.errors.category?.message}
          hint="Transport vertical. Any code is accepted."
        >
          <Input
            id="category"
            list="provider-categories"
            invalid={Boolean(form.formState.errors.category)}
            {...form.register('category')}
          />
          <datalist id="provider-categories">
            {SUGGESTED_PROVIDER_CATEGORIES.map((category) => (
              <option key={category} value={category} />
            ))}
          </datalist>
        </Field>
      </div>

      <Field
        label="Display name"
        htmlFor="displayName"
        error={form.formState.errors.displayName?.message}
      >
        <Input
          id="displayName"
          invalid={Boolean(form.formState.errors.displayName)}
          {...form.register('displayName')}
        />
      </Field>

      <Field
        label="Base URL"
        htmlFor="baseUrl"
        error={form.formState.errors.baseUrl?.message}
        hint="Leave blank when the adapter supplies its own host (the mock provider has none)."
      >
        <Input
          id="baseUrl"
          inputMode="url"
          placeholder="https://global.api.flixbus.com"
          invalid={Boolean(form.formState.errors.baseUrl)}
          {...form.register('baseUrl')}
        />
      </Field>

      <Controller
        control={form.control}
        name="capabilities"
        render={({ field, fieldState }) => (
          <fieldset className="flex flex-col gap-2.5">
            <legend className="mb-1 text-caption font-medium text-content-secondary">
              Capabilities
            </legend>
            <p className="mb-1 text-caption text-content-muted">
              Callers route work by this list. Declaring something the adapter cannot do turns a
              clean &ldquo;not supported&rdquo; into a failed request at call time.
            </p>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {PROVIDER_CAPABILITIES.map((capability) => {
                const checked = field.value.includes(capability);
                return (
                  <label
                    key={capability}
                    className={cn(
                      'flex cursor-pointer items-center gap-2.5 rounded-md border px-3 py-2.5 transition-colors',
                      checked
                        ? 'border-accent/40 bg-accent-soft text-content'
                        : 'border-line bg-elevated text-content-secondary hover:border-line-strong',
                    )}
                  >
                    <input
                      type="checkbox"
                      className="size-4 accent-[var(--color-accent)]"
                      checked={checked}
                      onChange={(event) => {
                        const next = event.target.checked
                          ? [...field.value, capability]
                          : field.value.filter((value: ProviderCapability) => value !== capability);
                        // Sorted so the payload matches the order the backend returns, which keeps
                        // form state and server state comparable.
                        field.onChange([...next].sort());
                      }}
                    />
                    <span className="text-caption">{humanizeCapability(capability)}</span>
                  </label>
                );
              })}
            </div>
            {fieldState.error && (
              <p role="alert" className="text-caption text-danger">
                {fieldState.error.message}
              </p>
            )}
          </fieldset>
        )}
      />

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <Field
          label="Timeout (ms)"
          htmlFor="timeoutMs"
          error={form.formState.errors.timeoutMs?.message}
          hint="Per request, 1–120000"
        >
          <Input
            id="timeoutMs"
            type="number"
            inputMode="numeric"
            invalid={Boolean(form.formState.errors.timeoutMs)}
            {...form.register('timeoutMs', { valueAsNumber: true })}
          />
        </Field>

        <Field
          label="Retries"
          htmlFor="retryCount"
          error={form.formState.errors.retryCount?.message}
          hint="Attempts after the first failure, 0–5"
        >
          <Input
            id="retryCount"
            type="number"
            inputMode="numeric"
            invalid={Boolean(form.formState.errors.retryCount)}
            {...form.register('retryCount', { valueAsNumber: true })}
          />
        </Field>
      </div>

      {apiError && apiError.fieldErrors.length === 0 && (
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

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel} disabled={submitting}>
            Cancel
          </Button>
        )}
        <Button type="submit" loading={submitting} loadingText="Saving…">
          {editing ? 'Save changes' : 'Register provider'}
        </Button>
      </div>
    </form>
  );
}
