import { describe, expect, it } from 'vitest';
import { MAPPING_FORM_DEFAULTS, isMappingFormField, mappingFormSchema } from './index';

/**
 * The client-side rules, asserted against what the backend actually enforces.
 *
 * <p>The point of these tests is as much what the schema *doesn't* do: uniqueness and identifier
 * format are the backend's to decide, and a client rule that guessed at either would reject
 * requests the platform accepts.
 */

const valid = {
  ...MAPPING_FORM_DEFAULTS,
  locationId: '0b6b5a1e-6f0e-4a1c-9a3f-2f1a5c8d7e4b',
  provider: 'FLIXBUS',
  providerCityId: '3da253ae-02ca-430c-87e5-22842065a77d',
};

describe('mappingFormSchema', () => {
  it('accepts a mapping carrying only a city id', () => {
    expect(mappingFormSchema.safeParse(valid).success).toBe(true);
  });

  it('accepts a mapping carrying only a station id', () => {
    const result = mappingFormSchema.safeParse({
      ...valid,
      providerCityId: '',
      providerStationId: 'station-1',
    });
    expect(result.success).toBe(true);
  });

  it('refuses a mapping with neither a city nor a station id', () => {
    const result = mappingFormSchema.safeParse({
      ...valid,
      providerCityId: '',
      providerStationId: '',
      providerStationName: 'MGBS',
    });

    // A station name alone cannot be looked up at the provider — this mirrors ProviderPlaceRef.
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['providerCityId']);
  });

  it('requires a canonical location', () => {
    const result = mappingFormSchema.safeParse({ ...valid, locationId: '' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((issue) => issue.path[0] === 'locationId')).toBe(true);
  });

  it('requires a provider', () => {
    const result = mappingFormSchema.safeParse({ ...valid, provider: '   ' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((issue) => issue.path[0] === 'provider')).toBe(true);
  });

  it('enforces the backend column limits and nothing tighter', () => {
    expect(mappingFormSchema.safeParse({ ...valid, provider: 'x'.repeat(50) }).success).toBe(true);
    expect(mappingFormSchema.safeParse({ ...valid, provider: 'x'.repeat(51) }).success).toBe(false);

    expect(mappingFormSchema.safeParse({ ...valid, providerCityId: 'x'.repeat(255) }).success).toBe(
      true,
    );
    expect(mappingFormSchema.safeParse({ ...valid, providerCityId: 'x'.repeat(256) }).success).toBe(
      false,
    );
  });

  it('accepts provider identifiers that are not UUIDs', () => {
    // The backend stores these as varchar(255); the seeded FlixBus data uses plain numeric ids.
    // A UUID rule here would reject values the platform already holds.
    const result = mappingFormSchema.safeParse({ ...valid, providerCityId: '58291' });
    expect(result.success).toBe(true);
  });

  it('leaves uniqueness entirely to the backend', () => {
    // Two identical payloads both parse. Whether the second collides is a question about rows the
    // form cannot see, answered by a 409 that names the offending field.
    expect(mappingFormSchema.safeParse(valid).success).toBe(true);
    expect(mappingFormSchema.safeParse(valid).success).toBe(true);
  });
});

describe('isMappingFormField', () => {
  it('recognises the fields a backend error can be attached to', () => {
    expect(isMappingFormField('providerCityId')).toBe(true);
    expect(isMappingFormField('locationId')).toBe(true);
  });

  it('rejects anything else, so an unknown field falls back to the form-level message', () => {
    expect(isMappingFormField('providerMetadata')).toBe(false);
    expect(isMappingFormField('nonsense')).toBe(false);
  });
});
