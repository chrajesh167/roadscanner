import { z } from 'zod';
import { PROVIDER_CAPABILITIES } from '@/lib/api/types';

/**
 * Mirrors the Bean Validation constraints on `ProviderRequest` field for field — same limits,
 * same meanings. Client validation exists to give an immediate answer, never to be the authority:
 * the backend re-validates everything, and a rule that disagrees with it would either block a
 * legal request or wave through an illegal one.
 *
 * The provider's `code` is create-only. The backend ignores it on update because sessions and
 * health records are keyed on it, so the edit form renders it read-only rather than sending a
 * value that would be silently discarded.
 */
export const providerFormSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, 'A code is required')
    .max(50, 'Code must be at most 50 characters')
    .regex(/^[A-Za-z0-9_-]+$/, 'Use letters, numbers, hyphens and underscores only'),

  category: z
    .string()
    .trim()
    .min(1, 'A category is required')
    .max(50, 'Category must be at most 50 characters'),

  displayName: z
    .string()
    .trim()
    .min(1, 'A display name is required')
    .max(255, 'Display name must be at most 255 characters'),

  capabilities: z
    .array(z.enum(PROVIDER_CAPABILITIES))
    .min(1, 'Select at least one capability'),

  // Optional at the backend, so an empty field must mean "not set" rather than "the empty string".
  baseUrl: z
    .string()
    .trim()
    .max(500, 'Base URL must be at most 500 characters')
    .refine((value) => value === '' || /^https?:\/\//i.test(value), {
      message: 'Base URL must start with http:// or https://',
    }),

  timeoutMs: z
    .number({ error: 'Timeout is required' })
    .int('Timeout must be a whole number of milliseconds')
    .min(1, 'Timeout must be greater than zero')
    .max(120_000, 'Timeout must be at most 120000ms'),

  retryCount: z
    .number({ error: 'Retry count is required' })
    .int('Retry count must be a whole number')
    .min(0, 'Retry count must be at least 0')
    .max(5, 'Retry count must be at most 5'),
});

export type ProviderFormValues = z.infer<typeof providerFormSchema>;

/** V6's column defaults, so an omitted field means the same thing here as in a migration. */
export const PROVIDER_FORM_DEFAULTS: ProviderFormValues = {
  code: '',
  category: 'BUS',
  displayName: '',
  capabilities: [],
  baseUrl: '',
  timeoutMs: 5_000,
  retryCount: 2,
};
