# Responsibilities

## In Scope

- Provider authentication, session management, token refresh
- Provider capability discovery
- Searching provider APIs for trips
- Seat map retrieval
- Seat blocking and release
- Booking confirmation
- Ticket retrieval
- Retry policies, circuit breakers, rate limiting, bulkheading around every outbound provider call
- Request/response mapping between RoadScanner's canonical model and each provider's own shape
- Provider error translation into a canonical exception hierarchy
- Provider health checks (active, scheduled, and on-demand)

## Explicitly Out of Scope

- **RoadScanner user identity/authentication** — `auth-service`'s concern. This service has no
  concept of a traveler; every operation is scoped to a provider session, not a user session.
- **Booking state** — `booking-service` owns the booking record itself (status, passenger
  history, cancellation policy application). This service returns a `BookingConfirmation` once,
  from the provider's own response, and retains none of it.
- **Inventory** — `inventory-service` owns RoadScanner's own seat-hold/availability state for its
  own bus operators. A `SeatReservation` returned by this service is a *provider's* temporary
  hold, a different concept entirely from an `inventory-service` hold (see `seat-locking-flow.md`)
  — the two are never conflated, and this service does not attempt to reconcile them.
- **Payments** — `payment-service`'s concern; this service never touches payment data.
- **Notifications** — `notification-service`'s concern.
- **The platform's own search index** — `search-service`'s concern; this service is what
  `search-service` (or, more precisely, whatever future component aggregates provider results
  into it) would call, not a replacement for it.

## Why the Boundary Is Drawn Here

Provider integrations are volatile in a way no other part of this platform is: each provider has
its own API shape, its own auth flow, its own rate limits, its own failure modes, and — per the
request that created this service — new providers are expected to be added on an ongoing basis.
Concentrating all of that volatility (and all of the resilience engineering it requires) in one
service means `booking-service`, `search-service`, and `inventory-service` can each depend on one
stable, canonical contract regardless of how many providers exist behind it or how often a
provider's own API changes.
