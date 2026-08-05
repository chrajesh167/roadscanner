'use client';

import * as React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/misc';
import { ApiError } from '@/lib/api/client';
import { cn } from '@/lib/utils/cn';
import { useProviders } from '@/features/providers/hooks';
import { LocationPicker } from './location-picker';
import {
  MAPPING_FORM_DEFAULTS,
  isMappingFormField,
  mappingFormSchema,
  type MappingFormValues,
} from '../schemas';
import type { LocationSummary, ProviderMapping, ProviderMappingRequest } from '../types';

/**
 * One form for creating and editing, because the backend takes one shape for both
 * (`ProviderMappingRequest`).
 *
 * <p>The difference is what identifies the mapping. `locationId` and `provider` are required on
 * create and ignored on update — together they say *which* translation this is, and changing
 * either would silently turn one mapping into a different one while carrying its verified flag and
 * sync history across. In edit mode both are therefore rendered read-only and omitted from the
 * payload rather than sent and discarded. Re-pointing a mapping is a delete plus a create.
 *
 * <p>The provider list comes from `provider-integration-service`'s registry — the platform's only
 * provider registry. The field stays a free-text input with a datalist rather than a closed
 * select: the backend's `ProviderCode` is an open string, and a console that could only map
 * providers already registered elsewhere would make onboarding order a hidden requirement.
 */
export function MappingForm({
  mapping,
  onSubmit,
  submitting,
  error,
  onCancel,
  /** Pre-selects the location, used when creating from an unmapped-worklist row. */
  initialLocation,
  initialProvider,
}: {
  mapping?: ProviderMapping;
  onSubmit: (body: ProviderMappingRequest) => void;
  submitting: boolean;
  error?: unknown;
  onCancel?: () => void;
  initialLocation?: LocationSummary;
  initialProvider?: string;
}) {
  const editing = mapping !== undefined;
  const providers = useProviders();

  // In edit mode the response already carries the location's display name and city, so the
  // read-only summary needs no extra request.
  const [location, setLocation] = React.useState<LocationSummary | null>(
    mapping
      ? {
          id: mapping.locationId,
          displayName: mapping.locationDisplayName,
          city: mapping.locationCity,
          state: null,
          country: '',
        }
      : (initialLocation ?? null),
  );

  const form = useForm<MappingFormValues>({
    resolver: zodResolver(mappingFormSchema),
    defaultValues: mapping
      ? {
          locationId: mapping.locationId,
          provider: mapping.provider,
          providerCityId: mapping.providerCityId ?? '',
          providerStationId: mapping.providerStationId ?? '',
          providerStationName: mapping.providerStationName ?? '',
          verified: mapping.verified,
        }
      : {
          ...MAPPING_FORM_DEFAULTS,
          locationId: initialLocation?.id ?? '',
          provider: initialProvider ?? '',
        },
  });

  // Field-level failures from the backend land on the field that caused them — the 409 for a
  // duplicate mapping names `locationId`, `providerCityId` or `providerStationId` precisely so the
  // operator knows which value to change. Anything else shows once at the foot of the form.
  const apiError = error instanceof ApiError ? error : null;
  React.useEffect(() => {
    if (!apiError) return;
    for (const fieldError of apiError.fieldErrors) {
      if (isMappingFormField(fieldError.field)) {
        form.setError(fieldError.field, { message: fieldError.message });
      }
    }
  }, [apiError, form]);

  const submit = form.handleSubmit((values) => {
    const blankToNull = (value: string) => (value.trim() === '' ? null : value.trim());

    onSubmit({
      // Omitted entirely when editing: the backend ignores both, and sending them would suggest
      // otherwise to anyone reading the request.
      ...(editing ? {} : { locationId: values.locationId, provider: values.provider.trim() }),
      providerCityId: blankToNull(values.providerCityId),
      providerStationId: blankToNull(values.providerStationId),
      providerStationName: blankToNull(values.providerStationName),
      // Preserved as-is: it is an opaque provider payload this console never parses, and a full
      // replace that dropped it would discard data no one here can reconstruct.
      providerMetadata: mapping?.providerMetadata ?? null,
      verified: values.verified,
    });
  });

  return (
    <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
      <Field
        label="Canonical location"
        htmlFor="location-picker"
        error={form.formState.errors.locationId?.message}
        hint={
          editing
            ? 'Not editable — the location and provider identify which mapping this is'
            : 'Must already exist in the catalogue. This never creates a location.'
        }
      >
        {editing ? (
          <div className="flex flex-col gap-0.5 rounded-md border border-line bg-elevated px-3.5 py-2.5">
            <span className="text-body text-content">{mapping.locationDisplayName}</span>
            <span className="text-caption text-content-muted">{mapping.locationCity}</span>
          </div>
        ) : (
          <Controller
            control={form.control}
            name="locationId"
            render={({ field }) => (
              <LocationPicker
                value={location}
                invalid={Boolean(form.formState.errors.locationId)}
                onChange={(next) => {
                  setLocation(next);
                  field.onChange(next?.id ?? '');
                }}
              />
            )}
          />
        )}
      </Field>

      <Field
        label="Provider"
        htmlFor="provider"
        error={form.formState.errors.provider?.message}
        hint={
          editing
            ? 'Not editable — re-pointing a mapping is a delete plus a create'
            : 'The provider code, as registered (e.g. FLIXBUS)'
        }
      >
        <Input
          id="provider"
          list={editing ? undefined : 'provider-codes'}
          readOnly={editing}
          disabled={editing}
          className={cn(editing && 'font-mono')}
          invalid={Boolean(form.formState.errors.provider)}
          {...form.register('provider')}
        />
        {!editing && (
          <datalist id="provider-codes">
            {(providers.data ?? []).map((provider) => (
              <option key={provider.id} value={provider.code}>
                {provider.displayName}
              </option>
            ))}
          </datalist>
        )}
      </Field>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <Field
          label="Provider city id"
          htmlFor="providerCityId"
          error={form.formState.errors.providerCityId?.message}
          hint="The provider's own identifier for the city"
        >
          <Input
            id="providerCityId"
            className="font-mono text-caption"
            placeholder="3da253ae-02ca-430c-87e5-22842065a77d"
            invalid={Boolean(form.formState.errors.providerCityId)}
            {...form.register('providerCityId')}
          />
        </Field>

        <Field
          label="Provider station id"
          htmlFor="providerStationId"
          error={form.formState.errors.providerStationId?.message}
          hint="Optional when a city id is supplied"
        >
          <Input
            id="providerStationId"
            className="font-mono text-caption"
            invalid={Boolean(form.formState.errors.providerStationId)}
            {...form.register('providerStationId')}
          />
        </Field>
      </div>

      <Field
        label="Provider station name"
        htmlFor="providerStationName"
        error={form.formState.errors.providerStationName?.message}
        hint="What the provider prints on a ticket. Labelling only — it is never used to look a place up."
      >
        <Input
          id="providerStationName"
          placeholder="MGBS"
          invalid={Boolean(form.formState.errors.providerStationName)}
          {...form.register('providerStationName')}
        />
      </Field>

      <Controller
        control={form.control}
        name="verified"
        render={({ field }) => (
          <div className="flex items-start justify-between gap-4 rounded-md border border-line bg-elevated px-3.5 py-3">
            <span className="flex flex-col gap-1">
              <span className="text-caption font-medium text-content-secondary">Verified</span>
              <span className="text-caption text-content-muted">
                Set this only once you have checked the identifiers against the provider&apos;s own
                console. It records a human judgement, not a test result.
              </span>
            </span>
            <Switch
              checked={field.value}
              onCheckedChange={field.onChange}
              aria-label="Mark this mapping verified"
            />
          </div>
        )}
      />

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
          {editing ? 'Save changes' : 'Create mapping'}
        </Button>
      </div>
    </form>
  );
}
