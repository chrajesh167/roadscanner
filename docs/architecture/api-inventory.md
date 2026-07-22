# API Inventory

This document lists each service's **public responsibility and endpoint categories** — what kind of operations it exposes and to whom. It intentionally stops short of concrete endpoints (paths, verbs, request/response shapes); those belong in `docs/api/` as OpenAPI contracts, written when a service is actually implemented, not guessed at today.

All client-facing categories are reached through `api-gateway` only (per `high-level-design.md` §2) — "consumed by" below names the client, not a direct connection.

## `api-gateway`

Not a domain API itself — it's the routing/auth-enforcement facade in front of every category below. Exposes unified entry points that route to the owning service, enforcing JWT validation and rate limiting uniformly regardless of which backend handles the request.

## `auth-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Registration & Login | Create an account, authenticate, obtain tokens | customer-web, operator-portal, admin-console |
| Token Management | Refresh an access token, revoke a session (logout) | all clients |
| Password Reset | Recover access to an account | customer-web, operator-portal |

## `user-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Profile Management | View/update traveler profile and contact details | customer-web |
| Saved Passenger Management | Manage passengers a traveler books on behalf of | customer-web |

## `operator-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Operator Onboarding | Apply for and review operator accounts | operator-portal, admin-console |
| Fleet Management | Manage buses, seat layouts, amenities | operator-portal |
| Route & Schedule Management | Manage routes, trip schedules, fares, cancellation policy | operator-portal |

## `inventory-service`

Corrected by architecture review, 2026-07-22 — `inventory-service` is a catalog/metadata service;
live seat availability, holds, and reservations moved to `provider-integration-service`. See
`docs/services/inventory-service/overview.md`.

| Category | Purpose | Consumed by |
|---|---|---|
| City / Station / Route Browsing | Catalog geography and route lookup | customer-web |
| Trip Metadata Query | Catalog shape for a trip (route, schedule, operator, fare) | customer-web, search-service (event-driven, not this API — see below), `booking-service` |
| Static Seat Layout Query | Seat numbering/deck/type — shape only, never status | customer-web, `booking-service` |
| Trip Availability Query *(facade)* | Live seat-count, proxied live to `provider-integration-service` via the trip's provider mapping | search-service — **unchanged contract**, internal behavior corrected |
| Provider Mapping Query | Provider type + native trip id for a catalog trip | `booking-service` (service-to-service) |
| Sync Status Query | Catalog synchronization health/history | Internal/admin tooling |

`search-service` reaches this service's catalog shape via Kafka (`inventory-service`'s own
`TripPublished`/`TripUpdated`/`TripCancelled`, not a REST call) — listed here for completeness,
not as an API category.

## `search-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Trip Search | Search trips by origin, destination, date | customer-web |
| Filter & Sort | Refine search results (price, time, rating, bus type) | customer-web |

## `booking-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Booking Creation | Start a booking against a held seat | customer-web |
| Booking Lifecycle Management | View, cancel a booking | customer-web, operator-portal (view own trip's bookings) |
| Ticket Retrieval | View/download a confirmed e-ticket | customer-web |

## `payment-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Payment Initiation | Start payment for a pending booking | customer-web |
| Payment Status | Check the status of an in-flight payment | customer-web |
| Refund | Trigger and track a refund | `booking-service` (service-to-service), admin-console (support overrides) |

## `notification-service`

No client-facing API category. It is a pure event consumer (see `event-catalog.md`) that dispatches email/SMS/push — nothing calls it directly except the platform's own event stream. Worth stating explicitly: not every service needs a public API surface.

## `analytics-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Platform Reporting Query | Booking/revenue/operational reporting | admin-console |

Otherwise a pure event consumer, same reasoning as `notification-service`.

## `review-service`

| Category | Purpose | Consumed by |
|---|---|---|
| Submit Review | Rate/review a completed trip | customer-web |
| Fetch Reviews | Read reviews for a trip/operator | customer-web (search result display), search-service (rating aggregation) |

## `provider-integration-service`

Unlike every category above, this one is **internal-only** — never reached through `api-gateway`,
never called by a client. It's the platform's sole gateway to external transportation providers
(FlixBus first); see `docs/services/provider-integration-service/overview.md`.

| Category | Purpose | Consumed by |
|---|---|---|
| Provider Session Management | Authenticate against a provider, refresh a session | `booking-service`, `inventory-service` (service-to-service) |
| Provider Trip Search | Search a specific provider for trips | `inventory-service` (service-to-service — catalog synchronization and the availability facade; **not** `search-service`, per boundary review point 4) |
| Provider Seat Operations | Retrieve a live seat map (read), block/release seats (write) | `inventory-service` (seat-map read, for the facade), `booking-service` (block/release, service-to-service) |
| Provider Booking | Confirm a booking, download a ticket | `booking-service` (service-to-service) |
| Provider Metadata | Capability discovery, health | `booking-service`, `inventory-service` (service-to-service) |

**`search-service` never calls this service, directly or indirectly through anything but
`inventory-service`'s existing availability facade** — corrected and made explicit by architecture
review, 2026-07-22, satisfying the requirement that search never becomes dependent on
provider-specific APIs.

## What's Deliberately Not Here

Concrete paths, HTTP verbs, request/response payloads, versioning scheme, pagination conventions, and error-response formats. Those are OpenAPI-contract decisions made per service in `docs/api/`, once implementation begins for that service — this document exists so that decision has a clear, agreed-upon scope to start from.
