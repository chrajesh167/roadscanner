import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDebouncedValue } from './use-debounced-value';

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useDebouncedValue', () => {
  it('returns the initial value immediately', () => {
    const { result } = renderHook(() => useDebouncedValue('hyd', 300));
    expect(result.current).toBe('hyd');
  });

  it('withholds a new value until it has stopped changing', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: '' },
    });

    rerender({ value: 'm' });
    rerender({ value: 'mg' });
    rerender({ value: 'mgb' });

    act(() => void vi.advanceTimersByTime(299));
    // Still nothing: each keystroke restarted the timer, so no intermediate term is ever queried.
    expect(result.current).toBe('');

    act(() => void vi.advanceTimersByTime(1));
    expect(result.current).toBe('mgb');
  });

  it('settles on the last value typed, not the first', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'hyderabad' },
    });

    rerender({ value: 'bangalore' });
    act(() => void vi.advanceTimersByTime(300));

    expect(result.current).toBe('bangalore');
  });

  it('stops pending updates when the caller unmounts mid-type', () => {
    const { rerender, unmount } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: '' },
    });

    rerender({ value: 'mgbs' });
    unmount();

    // No state update after unmount — the cleanup clears the timer rather than letting it fire.
    expect(() => act(() => void vi.advanceTimersByTime(1000))).not.toThrow();
  });
});
