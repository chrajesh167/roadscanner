# Inventory Service — Overview

> **Corrected by architecture review, 2026-07-22.** The previous version of this directory modeled
> `inventory-service` as the owner of live seat availability and Redis-backed seat holds, built
> directly off `docs/architecture/seat-locking-flow.md`. That document predates
> `provider-integration-service`, which now owns exactly that state in its own domain model
> (`ProviderSeatMap`, `SeatReservation`, `BlockSeat`/`ReleaseSeat`/`ConfirmBooking`). This version
> corrects the ownership split platform-wide; see the corresponding updates to
> `docs/architecture/seat-locking-flow.md`, `booking-flow.md`, `service-boundaries.md`, and
> `event-catalog.md`.

## Purpose

`inventory-service` is the platform's **catalog and metadata service** — cities, stations,
routes, operators (referenced), buses (referenced), trip metadata, static seat layouts, provider
mappings, schedule metadata, fare snapshots, and catalog synchronization state. It answers "what
trips exist and what do they look like," never "is this specific seat available right now" —
that question is answered live, through `provider-integration-service`, every time it's asked.

## Bounded Context

**In:** the full catalog surface listed above, plus the `ProviderMapping` that joins a canonical
catalog trip to a `provider-integration-service` `ProviderType` + native trip id, plus the
synchronization metadata that keeps that mapping current for both first-party
(`operator-service`) and third-party (provider-sourced) supply.

**Out — never owned here:**

- **Live seat availability, seat locks, seat reservations** — `provider-integration-service`.
- **Booking state, tickets, payment** — `booking-service` (not yet built) and `payment-service`.
- **Provider authentication, provider sessions, retries, circuit breakers, provider health** —
  `provider-integration-service`.
- **Route/schedule/fare/fleet configuration as source of truth for first-party operators** —
  `operator-service`; inventory holds a kept-current copy, same posture `search-service` already
  has toward the same upstream.
- **Search ranking, filtering, result composition** — `search-service`.

## Where It Sits

- `docs/architecture/high-level-design.md` §3 (service inventory) and §12 (extensibility) —
  updated by this review to reflect the corrected split.
- `docs/architecture/service-boundaries.md`'s `inventory-service` entry — updated.
- `docs/services/provider-integration-service/overview.md` — the service this one calls, as a
  client, for every question about live state.
- `docs/services/search-service/boundaries.md` — the service this one answers, unchanged in
  contract, changed in what backs the answer (see `boundaries.md` §"Relationship to
  `search-service`").

## Two Supply Sources, One Catalog

`inventory-service` is the aggregation point for trips regardless of where they come from:

1. **First-party** — `operator-service` operators, ingested via `TripPublished`/`TripUpdated`/
   `TripCancelled` (unchanged producer, unchanged payload).
2. **Third-party** — providers reachable through `provider-integration-service`
   (FlixBus, RedBus, AbhiBus, KSRTC, IntrCity, ...), ingested via a scheduled/triggered
   **catalog synchronization** process that queries `provider-integration-service`'s search
   capability and reconciles results into canonical catalog entries plus `ProviderMapping` rows.

This is why `inventory-service`, not `operator-service`, is now the producer `search-service`
consumes from for trip existence/shape — `operator-service` only ever knows about source (1). See
`events-published.md` and `events-consumed.md` for the corrected event flow this implies.

## Relationship to Other Services

- **Consumes** `operator-service`'s `TripPublished`/`TripUpdated`/`TripCancelled`,
  `RouteUpdated`, `OperatorUpdated` — first-party ingestion.
- **Calls** `provider-integration-service` synchronously, in two roles: (a) as a **facade**,
  answering `search-service`'s existing availability query by resolving a `ProviderMapping` and
  asking the provider live; (b) as a **catalog-sync client**, periodically searching each enabled
  provider to discover and reconcile third-party trips.
- **Publishes** its own `TripPublished`/`TripUpdated`/`TripCancelled` (the canonical, merged
  catalog view), `RouteUpdated`, `OperatorUpdated`, `FareSnapshotUpdated`,
  `CatalogSyncCompleted`/`CatalogSyncFailed` — `events-published.md`.
- **Is called synchronously** by `search-service` (availability facade, catalog metadata),
  `customer-web` (catalog browsing, static seat layout), and `booking-service` (catalog + fare +
  provider mapping lookup at hold/booking time) — `api-summary.md`.

## Design Principles Carried From the Platform Level

- Own database, no shared schema (`docs/architecture/database-ownership.md`).
- Stateless request handling (NFR-3) — nothing here is request-scoped in-process state.
- **Redis, if used at all, is cache only** — never a system of record for anything in this
  service, unlike the exception the old version of this document carved out. That exception now
  belongs entirely to `provider-integration-service`.
- New providers plug in without touching this service's business logic, mirroring
  `provider-integration-service`'s own extensibility story — a new provider means a new
  `ProviderMapping` population source (the sync process already treats "which providers are
  enabled" as data, from `provider-integration-service`'s own configuration), not new code here.

## Documents in This Directory

| Document | Covers |
|---|---|
| `responsibilities.md` | Explicit responsibilities, non-responsibilities |
| `boundaries.md` | The catalog/live split, the availability facade, and every service relationship |
| `domain-model.md` | City, Station, Route, Trip, TripSchedule, SeatLayout, ProviderMapping, FareSnapshot, sync metadata |
| `use-cases.md` | Catalog browsing, the availability facade, catalog synchronization |
| `sequence-diagrams.md` | First-party ingestion, provider catalog sync, the availability facade call, booking-time lookup |
| `data-ownership.md` | What's authoritative here vs. a kept-current copy vs. a live pass-through |
| `events-published.md` | The canonical `TripPublished`/`TripUpdated`/`TripCancelled` plus catalog events |
| `events-consumed.md` | From `operator-service` only |
| `api-summary.md` | Including the unchanged `search-service` availability contract |
