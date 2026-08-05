'use client';

import * as React from 'react';

/**
 * Trails a fast-changing value, settling only once it has stopped moving.
 *
 * <p>Used for the search boxes, which drive query keys directly: without this, every keystroke is
 * a cache entry and a request, and the answers can land out of order. Debouncing the *value*
 * rather than the request keeps that entirely inside TanStack Query's model — the key changes
 * once, so there is one fetch and one cached result per settled term.
 *
 * <p>The typed value stays uncontrolled and instant on screen; only what the server is asked
 * about waits.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [settled, setSettled] = React.useState(value);

  React.useEffect(() => {
    if (Object.is(value, settled)) return;

    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs, settled]);

  return settled;
}
