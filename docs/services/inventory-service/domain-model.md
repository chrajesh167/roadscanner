# Inventory Service — Domain Model

Conceptual model — shapes and invariants, not SQL. Physical schema is an implementation decision
made when the service is built, same convention every other service's domain-model doc follows.

## Why This Sits Between `search-service`'s Model and a True Aggregate Model

`inventory-service` protects real invariants (a `ProviderMapping` must reference a provider that
actually exists and actually supports search; a cancelled `Trip` never un-cancels), but — unlike
the old version of this document claimed — it does **not** protect the one invariant that would
make it a high-contention aggregate root ("at most one hold per seat"). That invariant now belongs
entirely to `provider-integration-service`. This service's aggregates are closer in spirit to
`operator-service`'s config-like data: real rules, low write contention, no locking concerns.

## Core Concepts

### City / Station (new to the platform)

`City` (name, state/region, country) and `Station` (belongs to a `City`; name, type — bus
stand/terminal, optional geo-coordinates) are structured catalog entities `inventory-service`
introduces. **Compatibility note:** neither `search-service` nor any existing event payload uses
these — `SearchQuery`/`Route` still take plain origin/destination strings, and that does not
change (`docs/services/search-service/domain-model.md`, unmodified by this review). `City`/
`Station` are `inventory-service`'s own structured route-definition scaffolding; every event this
service publishes still denormalizes origin/destination to display strings, so nothing downstream
needs to adopt city/station ids to keep working.

### Route

Origin/destination (as `City`/`Station` references internally), used to define `Trip`s and to
drive catalog synchronization (`inventory-service` searches each provider *by* route). Distinct
from `search-service`'s `Route` value object, which remains a display-string pair — this is the
structured, catalog-owned version underneath it.

### Operator (referenced, not owned)

`operatorId` + denormalized display fields, kept current via `OperatorUpdated`. Same
non-authoritative relationship `search-service` already has to `operator-service`'s trip events.

### Bus (referenced, not owned)

`busId` + a denormalized seat-layout template, kept current via `operator-service`'s fleet events.
The source `SeatLayout` (below) is materialized from.

### Trip (renamed from the old `TripInstance` — metadata only now)

The canonical, merged representation of a bookable trip. Holds:

- a trip identifier — the same id `search-service` uses, unchanged
- route, schedule (`TripSchedule`), operator id, bus id, current `FareSnapshot`
- a bookable flag — false on `TripCancelled`, terminal, matching every other cancel-flag on this
  platform
- a `ProviderMapping` (present only for provider-sourced trips; absent for first-party trips with
  no provider equivalent)
- a supply-origin marker — `FIRST_PARTY` or `PROVIDER_SYNCED` — so the catalog-sync process can
  tell its own previously-created rows apart from `operator-service`-ingested ones

**Deliberately not held:** anything about current seat occupancy. That question is never asked of
this entity — see `SeatLayout` below and `provider-integration-service` for where it's actually
answered.

### TripSchedule

Departure/arrival time; a distinct entity (rather than fields inlined on `Trip`, as the old model
had them) specifically so recurrence metadata (a schedule that repeats daily/weekly) has somewhere
to live later without reshaping `Trip` — recurrence itself is not designed here, no functional
requirement asks for it yet.

### SeatLayout (static — the corrected replacement for the old `SeatMap`)

Seat numbering, deck configuration, seat type (sleeper/seater), wheelchair-accessible flag,
physical position. **Contains no status field of any kind** — not available, not booked, not
held. This is the hard line your review requires: a `SeatLayout` describes what a bus's seats
*are*, never what they currently *contain*. Materialized once, from `operator-service`'s
fleet/bus snapshot (first-party) or from a provider's seat-map response during catalog sync
(provider-sourced), and essentially immutable thereafter — a bus's physical configuration doesn't
change per trip.

### ProviderMapping (new, load-bearing)

The seam between this catalog and the live world:

- `tripId` — this service's own canonical id
- `providerType` — the exact value `provider-integration-service`'s `ProviderType` uses (an open
  code, not an enum here either — new providers need no schema change)
- `providerTripId` — the provider's own opaque trip identifier, exactly as
  `provider-integration-service`'s `ProviderTrip.providerTripId` returns it
- `lastSyncedAt`, `syncStatus` — see `SyncRecord` below

A `Trip` with no `ProviderMapping` is a pure first-party trip with no third-party equivalent — not
an error state.

**Cardinality, confirmed by architecture review, 2026-07-22:** at most **one** `ProviderMapping`
per `Trip`. `ProviderMapping` is the only bridge between a canonical `Trip` and a provider
(`Trip → ProviderType → providerTripId`) — nothing else in this domain model, and no other
service, is permitted to hold that association. A `Trip` is never mapped to more than one
provider at a time under the current model; if the same physical departure is genuinely offered
through two different channels (e.g., a first-party operator who also lists the same bus through
a third-party provider), that is represented as **two distinct `Trip` rows**, each with its own
identity and at most its own single mapping, not one `Trip` with multiple `ProviderMapping`s.
**Flagged, not solved, here:** this means such a case would appear twice in search results today
with no automatic deduplication — a real future consideration (worth a `Trip`-to-`Trip`
"same physical departure" link if it becomes a real scenario), explicitly out of scope for Phase 1
since no current requirement or known operator relationship needs it solved yet.

### FareSnapshot

Last-observed fare for display/ranking — non-authoritative, same posture as
`search-service`'s identically-named value object. Refreshed by `operator-service` events
(first-party) or by catalog synchronization (provider-sourced).

### SyncRecord (synchronization metadata)

Per provider (or per operator, for completeness): last sync attempt time, status (`SUCCESS`/
`FAILED`/`IN_PROGRESS`), a catalog version marker, and error detail on failure. Backs
`CatalogSyncCompleted`/`CatalogSyncFailed` (`events-published.md`) and the operational visibility
your synchronization requirement (§7) asks for.

## Summary Table

| Concept | Kind | Authority | Kept Current Via |
|---|---|---|---|
| `City`, `Station` | Catalog structure | Owned outright | Administrative/catalog-management (not event-driven) |
| `Route` | Catalog structure | Owned outright | Same |
| `Operator`, `Bus` | Reference/denormalized copy | Not authoritative | `OperatorUpdated`, fleet events from `operator-service` |
| `Trip` | Aggregate, merged catalog view | Owned outright (as a merge) | `TripPublished`/`TripUpdated`/`TripCancelled` (first-party) or catalog sync (provider-sourced) |
| `TripSchedule` | Value object, embedded in `Trip` | Owned outright | Same as `Trip` |
| `SeatLayout` | Aggregate, shape only | Owned outright | Materialized once per trip, effectively immutable |
| `ProviderMapping` | Aggregate | Owned outright, exists only here | Catalog synchronization |
| `FareSnapshot` | Value object | Non-authoritative snapshot | `operator-service` events or catalog sync |
| `SyncRecord` | Aggregate | Owned outright | This service's own sync process |
| Live seat availability / holds | **Not modeled here at all** | `provider-integration-service` | N/A |
