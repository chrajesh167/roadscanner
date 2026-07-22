# Inventory Service — Use Cases

Four kinds of use case: **client-facing** (via `api-gateway`), **service-to-service** (direct
calls, no gateway), **internal/event-driven**, and **operational/scheduled**. See `domain-model.md`
for the shapes these operate on.

## Client-Facing

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Browse Cities / Stations** | `customer-web` search-form autocomplete | Prefix/text lookup over `City`/`Station` | New capability, not previously modeled anywhere on the platform |
| **Get Route / Trip Metadata** | `customer-web` trip-detail view | Return the `Trip`'s catalog shape (route, schedule, operator, fare snapshot, bookable flag) | No live seat data of any kind |
| **Get Static Seat Layout** | `customer-web` seat-selection UI, first paint | Return `SeatLayout` — numbering, deck, type, wheelchair flag | FR-2.4's shape half. The live-status half is fetched separately, by `booking-service`, not here — see `boundaries.md` |

## Service-to-Service (No Gateway)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Get Trip Availability** *(the facade)* | `search-service`'s existing cached overlay call | Resolve the trip's `ProviderMapping`; call `provider-integration-service` live for the seat count; return `{availableSeats: <int>}` | **Unchanged contract** — see `api-summary.md`. Internally a pass-through now, not an owned answer |
| **Get Trip Metadata + Provider Mapping** | `booking-service`, composing the seat-selection view or validating before a hold | Return `Trip`, `SeatLayout`, `FareSnapshot`, and `ProviderMapping` (provider type + native trip id) together | `booking-service` uses the mapping to call `provider-integration-service` itself for live status/hold — `inventory-service` does not proxy that part |

## Internal (Event-Driven — First-Party Ingestion)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Ingest Published Trip** | `TripPublished` (from `operator-service`) | Create/upsert a `Trip` (supply origin `FIRST_PARTY`), its `TripSchedule`, and materialize `SeatLayout` from the event's layout snapshot | Upsert keyed by trip id, matching `search-service`'s identical handling |
| **Ingest Updated Trip** | `TripUpdated` (from `operator-service`) | Overwrite shape fields (route, schedule, fare) | Never touches `SeatLayout` shape once materialized |
| **Ingest Cancelled Trip** | `TripCancelled` (from `operator-service`) | Set `bookable = false` | Idempotent, terminal — no un-cancel |
| **Ingest Route Update** | `RouteUpdated` (from `operator-service`) | Update the referenced `Route`'s shape | New event — see `events-consumed.md` |
| **Ingest Operator Update** | `OperatorUpdated` (from `operator-service`) | Update denormalized operator display fields | New event |

## Operational (Scheduled — Provider Catalog Synchronization)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Synchronize Provider Catalog** | Scheduled (e.g., periodic per provider) or on-demand (admin-triggered) | For each enabled provider (per `provider-integration-service`'s own configuration), search known routes/date windows; reconcile results into `Trip`/`SeatLayout`/`FareSnapshot`/`ProviderMapping` rows (supply origin `PROVIDER_SYNCED`); record a `SyncRecord`; publish `CatalogSyncCompleted` or `CatalogSyncFailed` | The exact matching heuristic (how a provider's trip is recognized as "the same" route/schedule RoadScanner already knows, vs. a genuinely new route) is an implementation decision, not fixed here — this is `inventory-service`'s answer to your synchronization requirement (§7) and its concrete mechanism for scaling to 100+ providers (§10) without new code per provider |
| **Publish Merged Catalog Change** | A direct consequence of either ingestion path above actually changing a `Trip`/`Route`/`Operator`/`FareSnapshot` | Publish the corresponding `TripPublished`/`TripUpdated`/`TripCancelled`/`RouteUpdated`/`OperatorUpdated`/`FareSnapshotUpdated` on this service's own topic | This is what `search-service` now consumes instead of `operator-service`'s raw feed — see `events-published.md` |

## What's Deliberately Not a Use Case Here

- **Seat hold creation, release, or validation** — moved to `provider-integration-service`,
  orchestrated by `booking-service`. The old version of this document modeled all three here;
  removed entirely, not merely relocated in wording.
- **Booking, payment, or ticketing of any kind.**
- **Anything requiring a provider-specific HTTP call** — every provider interaction, including
  catalog sync, goes through `provider-integration-service`'s canonical port, never a
  provider-specific client living in this service.
