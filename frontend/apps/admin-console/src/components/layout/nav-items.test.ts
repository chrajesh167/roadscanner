import { describe, expect, it } from 'vitest';
import { NAV_ITEMS, activeNavHref } from './nav-items';

describe('activeNavHref', () => {
  it('matches an exact route', () => {
    expect(activeNavHref('/providers')).toBe('/providers');
  });

  it('keeps the parent section highlighted on a detail route', () => {
    expect(activeNavHref('/providers/0b6b5a1e-6f0e-4a1c-9a3f-2f1a5c8d7e4b')).toBe('/providers');
  });

  it('returns null off-nav, so nothing is highlighted on the login screen', () => {
    expect(activeNavHref('/login')).toBeNull();
  });

  it('does not match a route that merely shares a prefix', () => {
    // `/health` must not light up for a hypothetical `/healthcheck`.
    expect(activeNavHref('/healthcheck')).toBeNull();
  });

  it('covers every declared nav item', () => {
    for (const item of NAV_ITEMS) {
      expect(activeNavHref(item.href)).toBe(item.href);
    }
  });
});
