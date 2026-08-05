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
  /**
   * search-service. Kept under one root so a mutation can invalidate every list, page and filter
   * combination at once — a created or deleted mapping changes which rows any of them contain,
   * and there is no way to know which page a row landed on without asking.
   */
  providerMappings: {
    all: ['provider-mappings'] as const,
    list: (filters: {
      provider: string | null;
      verified: boolean | null;
      search: string;
      page: number;
      size: number;
    }) => ['provider-mappings', 'list', filters] as const,
    /** The worklist is per provider — "unmapped" has no meaning without one. */
    unmapped: (provider: string | null, search: string) =>
      ['provider-mappings', 'unmapped', { provider, search }] as const,
  },
  locations: {
    all: ['locations'] as const,
    search: (term: string) => ['locations', 'search', term] as const,
  },
} as const;
