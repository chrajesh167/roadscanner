import { describe, expect, it } from 'vitest';
import { credentialsFormSchema, toCredentialsRequest } from './schema';

describe('credentialsFormSchema', () => {
  it('rejects a credential set with neither a password nor a token', () => {
    const result = credentialsFormSchema.safeParse({
      partnerEmail: 'partner@roadscanner.com',
      partnerPassword: '',
      partnerToken: '',
    });

    expect(result.success).toBe(false);
  });

  it('accepts a password alone', () => {
    expect(
      credentialsFormSchema.safeParse({
        partnerEmail: '',
        partnerPassword: 'hunter2',
        partnerToken: '',
      }).success,
    ).toBe(true);
  });

  it('accepts a token alone — some providers issue one instead of an account', () => {
    expect(
      credentialsFormSchema.safeParse({
        partnerEmail: '',
        partnerPassword: '',
        partnerToken: 'tok_live_123',
      }).success,
    ).toBe(true);
  });

  it('rejects a malformed email but allows it to be omitted', () => {
    expect(
      credentialsFormSchema.safeParse({
        partnerEmail: 'not-an-email',
        partnerPassword: 'hunter2',
        partnerToken: '',
      }).success,
    ).toBe(false);

    expect(
      credentialsFormSchema.safeParse({
        partnerEmail: '',
        partnerPassword: 'hunter2',
        partnerToken: '',
      }).success,
    ).toBe(true);
  });
});

describe('toCredentialsRequest', () => {
  it('sends null for an omitted field, never an empty string', () => {
    const body = toCredentialsRequest({
      partnerEmail: '',
      partnerPassword: 'hunter2',
      partnerToken: '',
    });

    // The write is a full replacement, so "" would store an empty secret and then report
    // hasToken: true for something that cannot authenticate.
    expect(body).toEqual({
      partnerEmail: null,
      partnerPassword: 'hunter2',
      partnerToken: null,
    });
  });

  it('preserves whitespace inside a secret', () => {
    const body = toCredentialsRequest({
      partnerEmail: '  partner@roadscanner.com  ',
      partnerPassword: '  spaced secret  ',
      partnerToken: '',
    });

    // The email is a normalisable identifier; the password is not — trimming it would store
    // something the partner never issued.
    expect(body.partnerEmail).toBe('partner@roadscanner.com');
    expect(body.partnerPassword).toBe('  spaced secret  ');
  });
});
