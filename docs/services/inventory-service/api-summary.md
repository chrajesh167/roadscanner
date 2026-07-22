# Inventory Service — API Summary

Category-level per `docs/architecture/api-inventory.md`'s convention, with the same one exception
the previous version of this document made: the availability-query shape is called out precisely,
because `search-service` code already ships against it.

## Operations

| Category | Purpose | Conceptual Input | Conceptual Output | Consumed By |
|---|---|---|---|---|
| City / Station Lookup | Autocomplete/browse | Prefix text | Matching cities/stations | `customer-web` |
| Route / Trip Metadata Query | Catalog shape for a trip | Trip id | Route, schedule, operator, fare snapshot, bookable flag | `customer-web`, `booking-service` |
| Static Seat Layout Query | Seat numbering/deck/type — shape only | Trip id | `SeatLayout` (no status) | `customer-web`, `booking-service` |
| **Trip Availability Query (the facade)** | Live seat-count, proxied | Trip id | Seat count | `search-service` — **unchanged contract**, see below |
| Provider Mapping Query | Provider type + native trip id for a catalog trip | Trip id | `ProviderType`, `providerTripId` | `booking-service` |
| Sync Status Query | Operational visibility into catalog synchronization | Provider type (optional filter) | Last sync time, status, catalog version | Internal/admin tooling |

## The Contract `search-service` Already Depends On — Unchanged

```
GET /api/v1/inventory/trips/{tripId}/availability → {"availableSeats": <int>}
```

Still binding, still served by `inventory-service`, still returns exactly this shape. What changed
is entirely internal: the answer is now a live pass-through to `provider-integration-service` via
the trip's `ProviderMapping`, not a read against this service's own hold/allocation state (which
no longer exists). `search-service` requires no code or contract change — see `boundaries.md`.

## What's Been Removed From This Service's Surface (Corrected by This Review)

- **Seat Hold Management (create/release).** Moved to `provider-integration-service`, orchestrated
  by `booking-service`. `inventory-service` exposes no hold-related endpoint of any kind, not even
  a facade — unlike the availability query, there is no reason for `booking-service` to go through
  `inventory-service` for this, since it already has the `ProviderMapping` from the metadata call
  and can call `provider-integration-service` directly.
- **Seat Hold Validation.** Same — `booking-service` validates directly against
  `provider-integration-service`, which already exposes exactly this shape
  (`docs/services/provider-integration-service/api-summary.md`).
- **Any booking, payment, or passenger-detail endpoint.** Never existed here, unchanged.

## What's Deliberately Absent (New Territory, Not Yet Needed)

- **Any provider-specific endpoint or parameter.** A caller of this service's API never knows or
  cares which provider (if any) backs a trip — that's exactly what `ProviderMapping` exists to
  hide behind a canonical trip id.
- **A live seat-status endpoint of any kind.** Not here, ever — see "What's Been Removed" above
  and `responsibilities.md`.

## Consumers

`customer-web` (via `api-gateway`) for catalog browsing; `search-service` and `booking-service`
(direct service-to-service, no gateway) for the internal categories — same direct-call pattern
`search-service`'s own `InventoryClientConfig` already uses today, unaffected by this review.
