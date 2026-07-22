# Inventory Service — Responsibilities

## Responsibilities

- **Catalog ownership** — cities, stations, routes: the structured geography and route
  definitions trips are built against (new to the platform — see `domain-model.md` for how this
  coexists with `search-service`'s existing plain-string origin/destination model without
  changing it).
- **Trip metadata** — the canonical, merged view of a bookable trip (route, schedule, operator,
  bus, fare snapshot, bookable flag), aggregated from both first-party (`operator-service`) and
  third-party (provider-synced) supply.
- **Static seat layout** — seat numbering, deck configuration, seat type, wheelchair-accessible
  seats, physical position (FR-2.4's seat-map view, the shape half of it — never the status half).
- **Provider mapping** — the join between a canonical trip and a `provider-integration-service`
  `ProviderType` + native trip id, kept current by catalog synchronization.
- **Fare snapshots** — the last-known fare for display/ranking, same non-authoritative posture
  `search-service`'s own `FareSnapshot` already models.
- **Synchronization** — provider mappings, sync history, sync status, catalog versioning: knowing
  when each provider's catalog was last reconciled and whether it succeeded.
- **The availability facade** — answering `search-service`'s existing
  `GET /trips/{tripId}/availability` by resolving the trip's `ProviderMapping` and asking
  `provider-integration-service` live, every time, with no caching of the answer itself.
- **Health, metrics, OpenAPI exposure** — non-negotiable per `.claude/ARCHITECTURE_RULES.md`.

## Non-Responsibilities

- **Live seat availability, seat holds, or seat reservations as owned state.** Never persisted,
  never cached as a system of record, never computed from this service's own tables. Every
  answer about live seat state is a pass-through call to `provider-integration-service`, made
  fresh. See `boundaries.md` and `data-ownership.md`.
- **Seat hold creation, release, or validation.** These are `provider-integration-service`
  capabilities, orchestrated by `booking-service` — not exposed here, not even as a facade. The
  previous version of this document had inventory-service own this outright; that was the central
  error this review corrects.
- **Booking state, tickets, or payment.** `booking-service`/`payment-service`, unchanged from the
  platform's general boundary.
- **Provider authentication, provider sessions, retries, circuit breakers, or provider health.**
  Entirely `provider-integration-service`'s concern. `inventory-service` is a *caller* of that
  service (for the sync process and the availability facade), never a manager of its sessions —
  each call authenticates or reuses a session the same way any other `provider-integration-service`
  caller would, with no special privilege or shared state.
- **Route/schedule/fare/fleet configuration as source of truth for first-party operators.** Still
  `operator-service`'s — inventory holds a kept-current copy via events, same as before.
- **Search ranking, filtering, or result composition.** `search-service`'s concern entirely.
- **Provider-specific HTTP integration of any kind.** `inventory-service` never imports a
  provider-specific type or calls a provider's API directly — every provider interaction, without
  exception, goes through `provider-integration-service`'s canonical, provider-agnostic port. This
  is the same rule `docs/services/provider-integration-service/boundaries.md` states from the
  other side.

## Design Rationale for the Split

The old rationale (inventory vs. `operator-service`: config-like vs. high-frequency transactional)
no longer applies to *this* service, because the high-frequency transactional half (holds,
availability) has moved to `provider-integration-service`. The rationale that replaces it:

- **vs. `provider-integration-service`:** catalog data changes when a route is added or a fare is
  revised — rare, planned, and safe to be eventually consistent. Live seat state changes on every
  hold, release, and booking, across every active trip, for every provider — exactly the
  volatility profile `provider-integration-service` is built (with Resilience4j, short-TTL caches,
  and no long-term persistence) to absorb. Merging the two back into one service would reintroduce
  the same load-shape conflict `docs/architecture/service-boundaries.md` already rejected once,
  for the same reason, just with a different pair of services.
- **vs. `operator-service`:** `operator-service` remains the source of truth for a first-party
  operator's own fleet/route/fare decisions. `inventory-service` never writes back to it and never
  represents itself as authoritative for that data — it denormalizes a copy, the same relationship
  `search-service` already has to the same events.

**Trade-off accepted:** a trip's full live picture (catalog shape + current seat state) now
requires composing two services' answers (`inventory-service` for shape,
`provider-integration-service` for state) wherever both are needed — most visibly in
`booking-service`'s seat-selection flow. Accepted for the same reason
`docs/architecture/service-boundaries.md` accepts the equivalent cost between `inventory-service`
and `booking-service` elsewhere on the platform: the alternative is one service tuned for two
incompatible jobs.
