/** Central key factory — every cache read/invalidation goes through here, never a literal array. */
export const queryKeys = {
  providers: {
    all: ['providers'] as const,
    list: (enabledOnly: boolean) => ['providers', 'list', { enabledOnly }] as const,
    detail: (id: string) => ['providers', 'detail', id] as const,
  },
  credentials: {
    all: ['credentials'] as const,
    detail: (providerId: string) => ['credentials', providerId] as const,
  },
  health: {
    all: ['health'] as const,
    /**
     * Keyed by provider *code*, not id: the health route is addressed by provider type. The probe
     * is a live call to the provider, so this cache is only ever filled by an explicit action —
     * see `features/health/hooks.ts`.
     */
    probe: (providerCode: string) => ['health', providerCode] as const,
  },
} as const;
