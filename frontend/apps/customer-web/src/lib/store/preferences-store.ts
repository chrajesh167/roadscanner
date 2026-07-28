'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { setMoneyPrecision } from '@/lib/utils/format';

/**
 * Purely client-side preferences.
 *
 * `user-service` owns Profile Management in the architecture but exposes no API yet, so there is
 * nowhere to persist these server-side. They are kept in localStorage and read by the UI directly;
 * when user-service ships, this store becomes the cache in front of it rather than the source.
 */
interface PreferencesState {
  /** Prefill the search form's origin on the landing page. */
  defaultOrigin: string;
  /** Show fares with decimals even when the amount is whole. */
  preciseFares: boolean;
  /** Suppress non-essential motion regardless of the OS setting. */
  reduceMotion: boolean;
  /** Opt out of the seat-hold countdown's urgent styling. */
  quietTimers: boolean;

  set: <K extends keyof Omit<PreferencesState, 'set' | 'reset'>>(
    key: K,
    value: PreferencesState[K],
  ) => void;
  reset: () => void;
}

const DEFAULTS = {
  defaultOrigin: '',
  preciseFares: false,
  reduceMotion: false,
  quietTimers: false,
};

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      ...DEFAULTS,
      set: (key, value) => set({ [key]: value } as Partial<PreferencesState>),
      reset: () => set({ ...DEFAULTS }),
    }),
    {
      name: 'roadscanner.preferences',
      onRehydrateStorage: () => (state) => {
        if (state) setMoneyPrecision(state.preciseFares);
      },
    },
  ),
);

// Keep the money formatter's module-level precision in step with the stored preference. Done as a
// subscription rather than inside `set` so rehydration and later toggles share one path.
usePreferencesStore.subscribe((state) => setMoneyPrecision(state.preciseFares));
