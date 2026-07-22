# Boundaries — Deeper Reasoning

## "No service is allowed to directly call FlixBus APIs" — enforced how?

This is a code-review/architecture-governance rule, not something Kafka topics or database
permissions can technically enforce (unlike `database-ownership.md`'s rule, which is enforced by
simply never granting another service credentials to this one's database). The mechanism that
makes it *cheap to follow* rather than merely *stated*: `adapter/out/provider/flixbus` is the only
package in the entire platform that imports FlixBus-specific types, and every operation FlixBus
supports is already exposed through this service's canonical, provider-agnostic REST API — so
there's never a reason for another service to reach around it. See `README.md`'s "How to Add a
New Provider" for the same reasoning applied to onboarding.

## Why sessions are stateful (Postgres + Redis) when the rest of this platform prefers stateless, event-derived state

Every other read-heavy service in this platform (`search-service`) builds a disposable, derived
read model from events. Provider sessions are different: they are the *authorization context* for
a stateful third-party negotiation (a seat block has to be confirmed or released against the
*same* session that created it, often within the same provider-side rate-limit window), not a
queryable projection of another service's data. There's no event stream to derive a session from
— it only exists because `AuthenticateProvider` was called — so it's owned, durable state here,
the same way `auth-service`'s refresh tokens are owned, durable state there.

## Why seat reservations/bookings/tickets are *not* persisted, even though sessions are

These are the results of a single provider round-trip, returned once, with no query pattern this
service itself ever needs to serve (nothing here answers "what did we last book with FlixBus" —
that's `booking-service`'s question, against its own data). Persisting them here would duplicate
`booking-service`'s eventual source of truth for no benefit, and would raise exactly the kind of
data-ownership ambiguity `database-ownership.md` exists to prevent. If a caller needs to correlate
a reservation with its own records, it does so using the identifiers this service returns
(`providerBlockReference`, `bookingReference`) — this service doesn't need to remember them to do
its job again later.

## Why capability discovery is the intersection of configuration and adapter, not either alone

A provider's `provider_configurations` row is this platform's *declared* intent (which
capabilities an operator/admin has configured this provider to expose); the resolved
`ProviderClient` adapter's `supportedCapabilities()` is what's *actually implemented* in code.
Advertising a capability that's configured but not coded (or coded but administratively disabled)
would let a caller attempt an operation that's guaranteed to fail one way or another — the
intersection is the only answer that's honest in both directions.

## Relationship to `inventory-service` and `booking-service` (once they exist)

Per the request's "Future Design Rules": `booking-service` and `inventory-service` are expected to
call this service synchronously (through its internal REST API) whenever they need live provider
data or need to execute a provider-side operation as part of their own workflow. Neither is
expected to hold this service's session token themselves for long — the natural pattern is
authenticate → do the provider operation(s) the current user request needs → let the session
expire or be swept, rather than one long-lived session shared across many unrelated requests.

`inventory-service` is specifically the caller for two purposes (confirmed by architecture review,
2026-07-22, of `docs/services/inventory-service/`): resolving live availability for its
`search-service`-facing facade, and periodic catalog synchronization (searching this service to
discover/reconcile provider trips into `inventory-service`'s own `ProviderMapping`). **`search-service`
never calls this service, in any capacity, direct or indirect beyond that facade** — it remains
provider-agnostic by construction, satisfying the platform's requirement that search never depend
on a provider-specific API.

## Known Gap: No Post-Confirmation Cancellation Capability

Identified by the same review: this service's inbound ports (`AuthenticateProvider`,
`RefreshSession`, `SearchTrips`, `GetSeatMap`, `BlockSeat`, `ReleaseSeat`, `ConfirmBooking`,
`DownloadTicket`, `GetProviderCapabilities`, `CheckProviderHealth`) have no operation for reversing
an *already-confirmed* booking with a provider — `ReleaseSeat` only covers a still-`BLOCKED`
reservation. `docs/architecture/booking-flow.md` steps 6–7 (post-confirmation cancellation, both
traveler- and operator-initiated) depend on a capability that doesn't exist yet here. This needs a
deliberate decision — a new `CancelBooking` port (mirroring `ConfirmBooking`'s shape) or an
accepted policy that post-confirmation cancellations are refund-only with no provider-side
reversal — before that part of the booking flow can be implemented. Not resolved in this pass; a
prerequisite for `booking-service` implementation, not for `inventory-service`'s.
