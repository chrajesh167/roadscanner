# Inventory Service — Boundaries

This deepens `docs/architecture/service-boundaries.md`'s (corrected) `inventory-service` entry
with this service's own relationship decisions. See `overview.md`'s banner for why this whole
directory was rewritten.

## The Central Design Point: Catalog Is Owned, Live State Is Never Cached as Truth

| Data | Authority | System | Why |
|---|---|---|---|
| Catalog shape (city, station, route, trip metadata, static seat layout, fare snapshot) | Owned copy (first-party) or owned original (provider-synced) — see `data-ownership.md` | Postgres | Changes rarely; safe to be eventually consistent and durably stored |
| Live seat state (availability, holds, reservations) | Never owned here, in any form, at any TTL | N/A — every answer is a fresh call to `provider-integration-service` | Changes constantly; owning even a cached copy here would recreate the two-systems-of-truth problem `provider-integration-service` already solved for itself |

The second row is the one the previous version of this document got backwards. It's worth being
precise about *why* "cache it briefly" isn't an acceptable middle ground here the way it is
elsewhere on this platform: `provider-integration-service` already runs a short-TTL seat-map cache
(`ProviderCache`, ~30s). A second cache in `inventory-service` in front of that one would be a
cache of a cache, adding staleness without adding correctness — the "two layers of staleness
tolerance" pattern `search-service`'s `data-ownership.md` describes only works when each layer
tolerates staleness for a *different* reason; here there'd be no second reason, just redundant
lag. `inventory-service` calls through to `provider-integration-service` fresh, every time.

## Relationship to `operator-service`

Unchanged in mechanism, expanded in scope: still one-directional, event-driven, no synchronous
call in either direction. Now consumes `RouteUpdated`/`OperatorUpdated` in addition to the
existing `TripPublished`/`TripUpdated`/`TripCancelled` — see `events-consumed.md`.

**Is a separate `operator-service` still justified, now that `inventory-service` holds a
canonical `Operator`/`Bus`/`Route` catalog too?** Yes — confirmed by architecture review,
2026-07-22. The two services answer different questions for different actors, and merging them
would recreate exactly the load-shape/responsibility conflict this whole document exists to avoid:

| Concern | Owner | Why not the other |
|---|---|---|
| Operator account lifecycle — onboarding, verification, admin approve/suspend (FR-5.1, FR-8.1) | `operator-service` | An identity/account-lifecycle concern, not a catalog fact — `inventory-service` has no concept of an "account," only a denormalized display record |
| The operator's own editable business configuration — fleet, routes, schedules, fares, cancellation policy (FR-5.2–5.4), as a **write model** with validation and business rules | `operator-service` | `inventory-service`'s `Route`/`Trip`/`Bus`/`FareSnapshot` are a **read-optimized, aggregated, cross-source projection** (first-party *and* provider-synced, `domain-model.md`) — a fundamentally different consumer and access pattern from one operator editing their own data through `operator-portal` |
| Settlement/payout summaries (FR-5.6) | `operator-service` | Financial reporting scoped to one operator — not a catalog fact by any reasonable bounded-context reading |
| Operator's view of their own bookings (FR-5.5, the "view bookings" half) | Composed from `booking-service`, surfaced via `operator-service` or `operator-portal` directly | Booking data, not catalog data |
| The canonical, cross-source, search/booking-facing representation of a trip | `inventory-service` | `operator-service` only ever knows about its own first-party operators — it has no visibility into provider-synced supply, and has no reason to; folding catalog aggregation into it would make it responsible for a second, incompatible job (matching `responsibilities.md`'s design rationale) |

This is the same **write-model/read-model split** already used everywhere else on this platform
(`operator-service` publishes; `inventory-service`, like `search-service` before it, denormalizes
a copy) — not a new pattern invented for this boundary, just this pattern applied to catalog
aggregation specifically. `operator-service` remains necessary; `inventory-service` does not
absorb it.

## Relationship to `provider-integration-service`

`inventory-service` is a **caller**, in two distinct roles:

1. **Availability facade** (per-request, driven by `search-service`'s or `booking-service`'s
   ask): resolve the trip's `ProviderMapping`, then call `provider-integration-service`'s search
   or seat-map capability for that specific provider trip, and return just what the caller asked
   for (a count, for `search-service`; full detail, for `booking-service`). `inventory-service`
   maintains whatever provider session this requires the same way any other
   `provider-integration-service` caller does — authenticate, reuse until near expiry, refresh —
   entirely as its own internal implementation detail. No session token from this process is ever
   exposed to `search-service`, `customer-web`, or any other caller of `inventory-service`; exact
   session lifecycle/caching mechanics are an implementation decision, not fixed here.
2. **Catalog synchronization** (scheduled/triggered, not per-request): periodically search each
   enabled provider (`provider-integration-service`'s own `GetProviderCapabilities`/configuration
   already exposes which providers are enabled) against `inventory-service`'s known routes and
   dates, and reconcile results into `Trip`/`ProviderMapping` rows. See `use-cases.md`.

`inventory-service` never imports a provider-specific type and never calls a provider's own API —
every one of the above goes through `provider-integration-service`'s canonical port, matching
`docs/services/provider-integration-service/boundaries.md`'s rule from the other side.

**Failure mode — deliberately the opposite of `provider-integration-service`'s own "fail
closed."** If a provider is unavailable when the availability facade is asked, `inventory-service`
must **degrade, not fail** — return an error/omit for that one trip's live count rather than
propagate a hard failure that would break `search-service`'s whole result page. This preserves
`search-service`'s *already-shipped* behavior unmodified: its `AvailabilityClient` already treats
any `RestClientException` (including a 5xx from `inventory-service`) as "unknown" and degrades
gracefully — so `inventory-service` returning an error status when
`provider-integration-service` can't answer is sufficient; no new "unknown" sentinel value needs
to be invented or added to the JSON contract. Correctness is never at stake on this path the way
it is for a hold — a missing seat count is a display gap, not an oversold seat
(`docs/services/search-service/boundaries.md`'s original "degrade, not fail" reasoning, still
correct, now inherited one hop further down the call chain).

## Relationship to `booking-service`

`booking-service` (not yet built) calls `inventory-service` for catalog facts it needs at two
points: validating a trip/fare before initiating a hold, and again when composing the
seat-selection view (static layout from `inventory-service`, live status from
`provider-integration-service`, composed by `booking-service` — see
`docs/architecture/booking-flow.md`, corrected by this review). `inventory-service` never calls
`booking-service` and has no knowledge of booking state.

**Boundary correction this review requires:** `docs/architecture/booking-flow.md` previously had
the *client* calling `inventory-service` directly to create a seat hold. Under the corrected
model that's impossible — `inventory-service` cannot create holds. `booking-service` becomes the
client-facing entry point for seat selection through booking, calling `inventory-service` for
metadata and `provider-integration-service` for live state and the hold itself. See the updated
`booking-flow.md`.

## Relationship to `search-service`

Unchanged synchronous contract (`api-summary.md`), **changed event source** (`events-published.md`,
`events-consumed.md`): `search-service` now consumes `TripPublished`/`TripUpdated`/`TripCancelled`
from `inventory-service`'s topic, not `operator-service`'s, because `inventory-service` is now the
only service that has visibility into the *full* catalog (first-party and provider-sourced
combined) — `operator-service` only ever knows about first-party trips. Without this change,
every third-party-provider trip would be permanently invisible to search, which would defeat a
large part of why `provider-integration-service` exists. The event payload shape is unchanged, so
this is a Kafka topic-source change for `search-service`, not a code or schema change — see
`docs/services/search-service/events-consumed.md`.

## Relationship to `customer-web`

Direct, read-only, catalog-only: city/station/route browsing, trip metadata, static seat layout —
`api-summary.md`. `customer-web`'s full interactive seat-selection experience (layout + live
status + hold) is fronted by `booking-service`, not `inventory-service` — `inventory-service`'s
own client-facing surface stays deliberately thin and non-interactive.

## What's Deliberately Out of Scope

- **Live seat availability, holds, reservations, provider auth/sessions** — see
  `responsibilities.md`.
- **Booking workflows of any kind** — `inventory-service` supplies facts; it never orchestrates a
  process. `booking-service` is the orchestrator per your `Inventory → Provider Integration →
  Payment → Ticket` flow.
- **Cross-vertical catalog** (train stations, flight routes) — Phase 2+, per
  `docs/architecture/high-level-design.md` §12.
