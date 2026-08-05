import { z } from 'zod';

/**
 * Mirrors the Bean Validation constraints on `ProviderMappingRequest`, plus the one rule that
 * lives a layer deeper in `ProviderPlaceRef`.
 *
 * <p>Client validation exists to give an immediate answer, never to be the authority: the backend
 * re-validates everything, and a rule that disagrees with it would either block a legal request or
 * wave through an illegal one. Nothing here is invented — each limit below is the `@Size` on the
 * matching Java field.
 *
 * <p>Two rules are deliberately **absent**, because they are the backend's alone to decide:
 *
 *   - **Uniqueness.** Whether a location already has a mapping for this provider, or a provider id
 *     is already claimed elsewhere, is a question about rows this form cannot see. The backend
 *     answers with a 409 naming the offending field, which the form attaches to that input.
 *   - **Identifier format.** The provider id fields are free strings at the backend
 *     (`varchar(255)`), not UUIDs. FlixBus happens to use UUIDs for cities; the seeded data uses
 *     plain numeric ids. Enforcing a UUID here would reject values the platform already stores.
 */
export const mappingFormSchema = z
  .object({
    /** Set from the location picker, never typed. Absent means nothing was chosen. */
    locationId: z.string().min(1, 'Choose a canonical location'),

    provider: z
      .string()
      .trim()
      .min(1, 'A provider is required')
      .max(50, 'Provider must be at most 50 characters'),

    providerCityId: z.string().trim().max(255, 'Provider city id must be at most 255 characters'),

    providerStationId: z
      .string()
      .trim()
      .max(255, 'Provider station id must be at most 255 characters'),

    providerStationName: z
      .string()
      .trim()
      .max(255, 'Provider station name must be at most 255 characters'),

    verified: z.boolean(),
  })
  /**
   * `ProviderPlaceRef` refuses a mapping carrying neither identifier, and the backend turns that
   * into a 400. Checked here too so the message lands on the field instead of arriving as a
   * whole-form error after a round trip — a station *name* alone cannot be looked up at the
   * provider, which is the reason for the rule.
   */
  .refine(
    (values) => values.providerCityId.trim() !== '' || values.providerStationId.trim() !== '',
    {
      message: 'Enter a provider city id or a provider station id — a station name alone cannot be looked up',
      path: ['providerCityId'],
    },
  );

export type MappingFormValues = z.infer<typeof mappingFormSchema>;

export const MAPPING_FORM_DEFAULTS: MappingFormValues = {
  locationId: '',
  provider: '',
  providerCityId: '',
  providerStationId: '',
  providerStationName: '',
  verified: false,
};

/** The form's own field names, so a backend `fieldError` can be matched against them safely. */
export const MAPPING_FORM_FIELDS = [
  'locationId',
  'provider',
  'providerCityId',
  'providerStationId',
  'providerStationName',
  'verified',
] as const satisfies readonly (keyof MappingFormValues)[];

export function isMappingFormField(field: string): field is keyof MappingFormValues {
  return (MAPPING_FORM_FIELDS as readonly string[]).includes(field);
}
