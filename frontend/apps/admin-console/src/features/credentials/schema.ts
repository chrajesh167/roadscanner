import { z } from 'zod';

/**
 * Mirrors `ProviderCredentialsRequest` plus the domain rule the record itself does not carry:
 * a credential set must contain at least a password or a token. That rule is enforced in the
 * domain so a non-REST caller cannot skip it; repeating it here is about telling the admin
 * before the round trip, not about being the authority.
 *
 * Every field is optional individually because a provider may authenticate by email/password, by
 * a pre-issued token, or by both.
 */
export const credentialsFormSchema = z
  .object({
    partnerEmail: z
      .string()
      .trim()
      .max(320, 'Email must be at most 320 characters')
      .refine((value) => value === '' || z.email().safeParse(value).success, {
        message: 'Enter a valid email address',
      }),
    partnerPassword: z.string(),
    partnerToken: z.string(),
  })
  .refine((values) => values.partnerPassword.length > 0 || values.partnerToken.length > 0, {
    message: 'Supply a password, a token, or both — a credential set with neither authenticates nothing',
    path: ['partnerPassword'],
  });

export type CredentialsFormValues = z.infer<typeof credentialsFormSchema>;

export const CREDENTIALS_FORM_DEFAULTS: CredentialsFormValues = {
  partnerEmail: '',
  partnerPassword: '',
  partnerToken: '',
};

/**
 * Turns form values into the wire body.
 *
 * <p>Empty strings become `null`, not `""`. The backend treats the payload as a full replacement,
 * and `""` is a value — sending it would store an empty secret and report `hasPassword: true`
 * for something that cannot authenticate.
 */
export function toCredentialsRequest(values: CredentialsFormValues) {
  return {
    // Trimmed here rather than relying on the schema's `.trim()`: the form submits raw values via
    // `getValues()`, so the parsed output this transform would otherwise produce never reaches
    // this function. An email is a normalisable identifier and surrounding whitespace in one is
    // always a typo.
    partnerEmail: values.partnerEmail.trim() === '' ? null : values.partnerEmail.trim(),
    // Not trimmed: leading or trailing whitespace is legal inside a secret, and silently
    // stripping it would store something the partner never issued.
    partnerPassword: values.partnerPassword === '' ? null : values.partnerPassword,
    partnerToken: values.partnerToken === '' ? null : values.partnerToken,
  };
}
