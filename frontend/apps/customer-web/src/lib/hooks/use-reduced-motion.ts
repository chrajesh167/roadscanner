'use client';

import { useReducedMotion } from 'framer-motion';
import { usePreferencesStore } from '@/lib/store/preferences-store';

/**
 * True when motion should be suppressed — either because the OS asks for it, or because the user
 * opted out in Settings. Every animated component reads this rather than framer-motion's hook
 * directly, so the in-app toggle is as authoritative as the system setting.
 */
export function usePrefersReducedMotion(): boolean {
  const systemPreference = useReducedMotion();
  const userPreference = usePreferencesStore((state) => state.reduceMotion);
  return Boolean(systemPreference) || userPreference;
}
