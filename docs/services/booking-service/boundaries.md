# Booking Service — Boundaries

This deepens `docs/architecture/service-boundaries.md`'s `booking-service` entry with this
service's own relationship decisions, mirroring the level of detail
`docs/services/inventory-service/boundaries.md` and
`docs/services/provider-integration-service/boundaries.md` already established. Every relationship
below is reviewed explicitly, per this specification's instructions — including the ones that
turn out to be "no relationship at all," which is worth stating as precisely as a real one.

## The Central Design Point: Booking Service Owns the Outcome, Never the Mechanics

| Concern | Owner | Why not `booking-service` |
|---|---|---|
| Is this trip real, what does it cost, what's its seat layout, which provider backs it | `inventory-service` | Catalog data changes rarely and is shared by every consumer of trip facts — duplicating it here would create a second, driftable copy |
| Is this specific seat available right now, can it be held, can the hold become a reservation | `provider-integration-service` | Live provider state is volatile, provider-specific, and already isolated behind one canonical port for the whole platform — re-implementing any of it here defeats that isolation |
| Did the traveler actually pay | `payment-service` (future) | Payment-gateway integration is its own volatile, compliance-sensitive concern (NFR-12) |
| Booking record, passenger details, status, history | **`booking-service`** | This is the one thing none of the above services has any reason to own — see `overview.md` |

`booking-service`'s own database therefore holds comparatively little data by volume, but it is
the *only* service that knows how all of the above facts fit together for one traveler's one
booking — see `data-ownership.md`.

## Relationship to `inventory-service`

**Synchronous, read-only, one direction.** `booking-service` calls `inventory-service` at exactly
two points in the flow (`docs/services/inventory-service/sequence-diagrams.md` flow 4, and
`api-summary.md`'s "Route / Trip Metadata Query... consumed by... booking-service"):

1. **Composing the seat-selection view** — trip metadata, static `SeatLayout`, and
   `ProviderMapping` together, before any provider call is made.
2. **Validating a trip before placing a hold** — the same three facts, re-confirmed immediately
   before `booking-service` calls `provider-integration-service` to block a seat, so a trip that
   became unbookable (`TripCancelled`) between the seat-selection view and the hold attempt is
   caught here rather than discovered as a confusing provider-side rejection.

`inventory-service` never calls `booking-service` and has no knowledge of booking state — this
matches `docs/services/inventory-service/boundaries.md`'s own "Relationship to `booking-service`"
section exactly, from the other side. **No REST contract of `inventory-service`'s changes as a
result of this specification** — `booking-service` is simply the caller `inventory-service`'s own
documentation already anticipated.

**Failure mode.** If `inventory-service` is unreachable or returns an error, `booking-service`
cannot proceed with a hold or a booking for that trip — unlike `inventory-service`'s own
"degrade, not fail" posture toward `search-service` (a missing seat count is a display gap),
a missing catalog fact here would mean placing a hold or creating a booking against a trip
`booking-service` cannot verify, which NFR-7 ("no double-booking, no lost payment... the system
should refuse the request rather than risk an inconsistent state") explicitly forbids trading
away for availability. `booking-service` fails the operation with a clear, retryable error instead.

### Recommendation (Not Implemented Here): `inventory-service` Should Expose "Bookable" as Its Own Signal

`inventory-service`'s `Trip.bookable` flag today means exactly one thing: *not cancelled*
(`docs/services/inventory-service/domain-model.md`: *"a bookable flag — false on `TripCancelled`,
terminal"*). It says nothing about whether the trip has a `ProviderMapping` at all. A first-party
trip with no provider equivalent reads as `bookable = true` right up until the moment
`booking-service` tries to hold a seat for it and fails — because a live hold requires a
`providerType` + `providerTripId` that simply doesn't exist for that trip (`overview.md`'s
ambiguity #2, `use-cases.md`'s "A Trip With No `ProviderMapping` Cannot Be Held"). **This means
`bookable = true` is currently a necessary but not sufficient condition for "can actually be
booked end-to-end"** — a caller has to separately check for `ProviderMapping` presence to know
the whole truth, which is exactly the kind of two-signals-for-one-question situation that invites
a caller (a future `booking-service` implementer, or `customer-web` if it ever reads this flag
directly) to get it wrong by trusting the flag alone.

**The honest fix would be for `inventory-service` to fold "has a usable `ProviderMapping`" into
what `bookable` means** — i.e., `bookable` becomes "not cancelled **and** has a live-booking
path," computed by `inventory-service` itself rather than inferred by every caller separately.
This is a real improvement worth making, and this review surfaces it explicitly because
`booking-service`'s own correctness depends on getting this check right today.

**Not implemented in this pass, on purpose** — this review's instructions are to document
`booking-service`, not to redesign `inventory-service`'s already-frozen domain model or change its
REST contract (`GET /trips/{tripId}` would need a field added or its `bookable` field's meaning
changed — either way, a contract change to a service these instructions explicitly forbid
redesigning). `booking-service`'s current design does not depend on this improvement happening —
it independently checks for `ProviderMapping` presence itself (`use-cases.md`'s "Hold Seats"),
which is correct today and remains correct even if `inventory-service` later adds a richer signal
(at that point, `booking-service`'s explicit check becomes redundant but harmless, and can be
simplified in a later revision of this document). **Recommended as a follow-up to
`inventory-service`'s own architecture, the next time that service's contract is revisited** — not
a blocker for `booking-service` implementation readiness.

## Relationship to `provider-integration-service`

**Synchronous, both reads and writes, one direction.** `booking-service` is one of exactly two
callers `provider-integration-service`'s own documentation names
(`docs/services/provider-integration-service/boundaries.md`'s "Relationship to `inventory-service`
and `booking-service`"), and the only one that calls its *write* operations
(`BlockSeat`/`ReleaseSeat`/`ConfirmBooking`/`DownloadTicket`) — `inventory-service` only ever calls
the read side (`SearchTrips`/`GetSeatMap`) for its catalog-sync and availability-facade roles.

`booking-service` follows the same session discipline
`provider-integration-service`'s own boundaries document prescribes for every caller: authenticate
(or reuse a still-valid session) immediately before the operation the current request needs, never
hold a session open across unrelated requests. Exact session-reuse/caching mechanics within
`booking-service` are an implementation decision, not fixed here — the same hedge
`inventory-service`'s own boundaries document applies to its identical situation.

`booking-service` never imports a provider-specific type and never calls a provider's API
directly — every provider interaction, without exception, goes through
`provider-integration-service`'s canonical, provider-agnostic port
(`docs/services/provider-integration-service/api-summary.md`).

**Failure mode.** Unlike the `inventory-service` relationship above, a failed provider call here
has a well-defined, correct outcome already documented at the platform level: `BlockSeat` failing
means "seat unavailable," reported to the traveler, no hold created, no booking created —
correctness preserved by construction, not by a fallback. `ConfirmBooking` failing *after* payment
already succeeded is the one edge case with a required remediation path — see "Known Gap:
Post-Confirmation Cancellation" below, which covers both this case and the symmetric
traveler-initiated cancellation.

### Known Gap: Post-Confirmation Cancellation

`docs/services/provider-integration-service/boundaries.md` identifies this gap explicitly and
defers its resolution to `booking-service`'s own design:

> *"This needs a deliberate decision — a new `CancelBooking` port (mirroring `ConfirmBooking`'s
> shape) or an accepted policy that post-confirmation cancellations are refund-only with no
> provider-side reversal — before that part of the booking flow can be implemented. Not resolved
> in this pass; a prerequisite for `booking-service` implementation, not for
> `inventory-service`'s."*

**Resolved here as: refund-only, no provider-side reversal, for both traveler-initiated
(`docs/architecture/booking-flow.md` step 6) and trip-cancellation-cascaded (step 7)
post-confirmation cancellation.** Concretely:

- `provider-integration-service`'s inbound ports
  (`AuthenticateProvider`/`RefreshSession`/`SearchTrips`/`GetSeatMap`/`BlockSeat`/`ReleaseSeat`/
  `ConfirmBooking`/`DownloadTicket`/`GetProviderCapabilities`/`CheckProviderHealth`) have no
  operation for reversing an already-confirmed booking. `ReleaseSeat` is documented as covering
  only a still-`BLOCKED` reservation (`docs/services/provider-integration-service/api-summary.md`'s
  "Reservation lifecycle — block, release").
- Adding a new port to `provider-integration-service` (a `CancelBooking` mirroring
  `ConfirmBooking`'s shape) would be the more complete fix, but is explicitly **not** done in this
  pass — it would mean redesigning an existing, frozen service's inbound port set and REST
  contract, which these instructions do not permit.
- `booking-service` therefore transitions the RoadScanner-side booking to `CANCELLED` and requests
  a refund (once `payment-service` exists — see `events-published.md`) **without attempting any
  provider-side call**. The provider continues to consider the reservation confirmed on its own
  side until whatever cancellation-fee/reversal process the provider itself exposes to its
  operator-facing tooling handles it — entirely outside this platform's visibility, by design,
  since providers are opaque systems this platform relays to, not manages
  (`docs/architecture/seat-locking-flow.md`'s "the provider's own system of record is
  authoritative, full stop").
- **This is a real product gap, not just a technical one** — a traveler who cancels a confirmed
  booking gets a RoadScanner-side refund, but the seat may not actually be released back to the
  provider's own inventory for resale. Flagged prominently here, in `overview.md`, and in
  `use-cases.md`'s "Cancel Booking," because it is a genuine limitation of the current platform,
  not an oversight in this documentation. **Recommended follow-up** (not designed further here):
  add a `CancelBooking` port to `provider-integration-service`, matching `ConfirmBooking`'s shape,
  the next time that service's contract is revisited.

### Known Gap: No Read-Only Reservation-Status Check

`docs/architecture/booking-flow.md` step 2 describes booking creation as validating the
reservation via "a read call" to `provider-integration-service` before persisting. That service's
actual, frozen port set has no such read-only operation — `GetSeatMap` returns per-seat status for
a *trip*, not the status of a specific *reservation*, and none of `BlockSeat`/`ReleaseSeat`/
`ConfirmBooking` is a safe, side-effect-free read.

**Resolved here as: booking-service performs a local expiry check instead of a fresh
provider-integration-service round-trip.** `BlockSeat`'s own response already returns
`expiresAt` (`docs/services/provider-integration-service/domain-model.md`'s `SeatReservation`).
`booking-service` records that timestamp in its own `SeatHold` record the moment the hold is
placed (see `domain-model.md`), and "validates the reservation" at booking-creation time by
checking `expiresAt` against the current clock — no additional call to
`provider-integration-service` is needed, because the TTL that call would be re-confirming was
already told to `booking-service` synchronously, seconds or minutes earlier, by the same service.
This satisfies the *intent* of booking-flow.md step 2 (never persist a booking against a hold that
may already be gone) without requiring a port that does not exist. See `use-cases.md`'s "Create
Booking" for the exact mechanism.

## Relationship to `payment-service` (Not Yet Built)

**Designed for, not yet real.** `booking-service`'s state machine and Kafka contracts are built
against `payment-service`'s documented events (`PaymentCompleted`/`PaymentFailed`/
`PaymentTimedOut`) and its documented refund-request capability
(`docs/architecture/payment-flow.md`'s "Refund Handling"), exactly the same "contract ready, no
real producer yet" posture `provider-integration-service` already carries for
`ProviderUnavailable`/`ProviderRecovered` (no consumers exist yet either) and
`inventory-service` carries toward `operator-service`'s events before that service existed.
`booking-service` consumes the payment events (`events-consumed.md`) and calls a refund-request
port (`use-cases.md`'s "Cancel Booking") whose adapter has no real target yet — this is a
documented, not-yet-closed integration point (`api-summary.md`), not a design gap.

**The one place the client legitimately bypasses `booking-service`, and why it doesn't break
single-orchestrator ownership.** `docs/architecture/booking-flow.md` step 3 and
`docs/architecture/api-inventory.md`'s `payment-service` row (*"Payment Initiation | Start payment
for a pending booking | customer-web"*) both document the client calling `payment-service`
**directly** to submit payment against a `PENDING_PAYMENT` booking — not through
`booking-service`. This is a deliberate, frozen platform decision, not a gap this specification
introduces: `payment-service` is its own client-facing service with its own boundary
(`docs/architecture/service-boundaries.md`'s `payment-service` entry), and PCI-scope reasoning
(NFR-12) argues *against* routing payment submission through an extra hop that would have no
reason to see payment details anyway. **This is not client-side orchestration of the booking
outcome** — the client is not deciding when to hold a seat, when to create a booking, or when to
confirm it with the provider; it is only submitting payment to the one service that owns payment
submission. `booking-service` never learns *how* payment happened and never drives the payment
step itself — it only reacts to `PaymentCompleted`/`PaymentFailed`/`PaymentTimedOut`
asynchronously (`sequence-diagrams.md` flows 4–5). Seat Hold, Booking creation, and Provider
Confirmation remain exclusively `booking-service`-orchestrated with no exception — see the
verification in "Single-Orchestrator Verification" below.

## Single-Orchestrator Verification

Reviewed explicitly, against every client-facing interaction this specification defines
(`sequence-diagrams.md`, `use-cases.md`): **the client never calls `inventory-service` or
`provider-integration-service` directly, at any point, in any flow.** Every sequence diagram in
this directory routes every such call through `booking-service` (`T->>BS`, never `T->>IS` or
`T->>PIS`). Concretely:

| Step | Who the client calls | Who actually talks to `inventory-service` / `provider-integration-service` |
|---|---|---|
| View seats | `booking-service` | `booking-service` (both) |
| Hold seat(s) | `booking-service` | `booking-service` (both, re-validation + `BlockSeat`) |
| Create booking | `booking-service` | Neither — local `SeatHold` check only (see "Known Gap: No Read-Only Reservation-Status Check") |
| Submit payment | `payment-service` (see above — a documented, deliberate exception, not a gap) | Neither |
| Provider confirmation | *(no client call — driven entirely by `PaymentCompleted`)* | `booking-service` |
| Cancel | `booking-service` | `booking-service` (`ReleaseSeat`, when applicable) |

`inventory-service` and `provider-integration-service` remain unreachable from `customer-web` in
this specification's design — consistent with `provider-integration-service` being internal-only
by its own `overview.md`, and with `inventory-service`'s client-facing surface being limited to
read-only catalog browsing (`docs/services/inventory-service/api-summary.md`), never seat
selection or holds. **Confirmed: `booking-service` is the sole orchestrator of Seat Hold, Booking
creation, and Provider Confirmation. The one client-service interaction outside `booking-service`
(payment submission) is a documented exception at the platform level, not an instance of the
client orchestrating the booking outcome.**

## Why `Hold Seats` Is a Separate Client-Facing Step, Not Folded Into `Create Booking`

Worth distinguishing explicitly from the previous section: `Hold Seats` being a *separate
client-facing REST endpoint* from `Create Booking` is a different question from *who orchestrates
it*. `booking-service` orchestrates both — the client never reaches `inventory-service` or
`provider-integration-service` to place a hold itself. The question here is narrower: should the
client see two distinct operations (hold, then later book), or one atomic operation that holds and
books in a single call?

**Kept as two steps, deliberately, for one reason that isn't optional: FR-3.2's own wording.**
*"The system places a temporary hold on selected seats **during checkout**"* — checkout is the
span of time the traveler spends entering passenger details, which takes real, human-scale time
(seconds to a couple of minutes), not the instant of a single API call. If `Hold Seats` and
`Create Booking` were merged into one atomic operation, the seat would only become held at the
*end* of that span — precisely when the traveler submits the form — which means two travelers
could both be filling out passenger details for the same seat simultaneously, only for one to
discover the conflict at submission. That is exactly the failure mode
`docs/architecture/seat-locking-flow.md` exists to prevent (*"a slow hold increases the odds of
losing the seat to a concurrent traveler,"* NFR-2), and exactly why FR-3.2 says "during checkout,"
not "at the moment of booking." A separate, earlier `Hold Seats` call is the only way to place the
hold *before* the traveler starts entering details rather than after.

This is also why `SeatHold` exists as a distinct, short-lived aggregate from `Booking`
(`domain-model.md`) rather than `Booking` simply gaining a `HOLDING` pre-status — FR-3.4's
abandonment case ("a traveler holds seats, looks at them, and never proceeds") needs to be
representable without ever creating a `Booking` row nobody would recognize as a real booking
attempt.

**What this is not:** it is not client-side orchestration of multiple backend services (see
"Single-Orchestrator Verification" above — both calls still terminate entirely at
`booking-service`), and it is not two independent API surfaces that could be called out of order
to bypass validation — `Create Booking` re-validates the hold's `expiresAt` and ownership on every
call, so a client cannot skip or race the two steps into an inconsistent outcome.

**Recommendation, not a change:** this specification recommends keeping `Hold Seats` as a
separate client-facing operation, as justified above. Collapsing it into `Create Booking` would
require weakening FR-3.2 itself (redefining "during checkout" to mean "at submission"), which is
outside this document's authority to decide — the same posture `domain-model.md`'s "Reconciling
the Requested State Vocabulary" takes toward changes that belong to a requirements document, not
an implementation specification.

## Relationship to `operator-service` (Not Yet Built)

`docs/architecture/booking-flow.md` step 6 describes checking "the applicable cancellation policy
via a synchronous call to `operator-service`" before a traveler-initiated post-confirmation
cancellation proceeds. `operator-service` does not exist yet (confirmed —
`docs/services/inventory-service/boundaries.md`'s own "Relationship to `operator-service`" table
lists it as owning exactly this configuration: *"Fares and a cancellation policy per route/trip"*,
FR-5.4).

**Not resolved here** — unlike the two `provider-integration-service` gaps above, this is not a
missing operation on an otherwise-complete service; it is a dependency on a service that has no
implementation, no API contract, and no domain model published anywhere yet, so there is nothing
concrete to design `booking-service`'s side of the integration against. This documentation
deliberately stops at: `booking-service` needs a synchronous `GetCancellationPolicy`-shaped call
to `operator-service`, keyed by trip (or route/operator), returning at minimum a cancellation-fee
schedule and a refund-eligibility window. **What `booking-service` does when this dependency is
unavailable — a specific default policy, or refusing cancellation outright until the dependency
exists — is an implementation decision left open, the same hedge `docs/architecture/booking-flow.md`
itself uses for the hold-token uniqueness mechanism ("the exact mechanism is a `booking-service`
implementation decision, not designed here").** See `use-cases.md`'s "Cancel Booking" and
`api-summary.md`'s "Remaining Integration Points" framing.

**Trip-cancellation-cascaded refunds (step 7) do not depend on this integration** — those are
documented as a full refund regardless of the normal fee schedule, a deliberate business rule
independent of `operator-service`'s per-traveler policy (`docs/architecture/booking-flow.md`
step 7). Only *traveler-initiated* post-confirmation cancellation needs the policy lookup.

## Relationship to `search-service`

**None.** `docs/services/search-service/boundaries.md` states this explicitly from its own side:
*"`search-service` is not on the booking or payment critical path in either direction."*
`booking-service` never calls `search-service`, is never called by it, and shares no event
relationship with it — a traveler moves from a search result to `booking-service` via the client
(`customer-web`), not via any service-to-service link. Stated here, precisely, for the same reason
`docs/services/inventory-service/boundaries.md` states its own relationships that turn out to be
"unchanged" or "none" explicitly rather than omitting them.

## Relationship to `notification-service` (Not Yet Built)

**Asynchronous, one direction, event-only.** `booking-service` publishes `BookingConfirmed` and
`BookingCancelled`; `notification-service` is documented as a consumer of both
(`docs/architecture/event-catalog.md`). `booking-service` never calls `notification-service`
directly and never decides notification content, timing, or channel — consistent with
`docs/architecture/service-boundaries.md`'s `notification-service` entry: *"that decision is
expressed by the event itself... not hardcoded in `notification-service`."* No degradation of
`notification-service` can ever block a booking operation (NFR-8) because there is no synchronous
path between the two services to begin with.

## Relationship to `review-service` (Not Yet Built)

**Synchronous, inbound, `review-service` is the caller.** `docs/architecture/service-boundaries.md`'s
`review-service` entry: *"it validates against `booking-service` at submission time rather than
duplicating booking state."* `booking-service` exposes one narrow, internal query answering "does
a verified, `COMPLETED` booking by this traveler for this trip exist" — backing FR-7.2 ("a review
can only be submitted against a verified, completed booking"). This is the only inbound
service-to-service call any other service makes against `booking-service` documented anywhere in
this platform's architecture. See `api-summary.md`'s "Booking Verification."

## Relationship to `analytics-service` (Not Yet Built)

**Asynchronous, one direction, event-only.** `analytics-service` consumes `BookingCreated`,
`BookingConfirmed`, and `BookingCancelled` for funnel/reporting purposes
(`docs/architecture/event-catalog.md`). Same non-blocking posture as `notification-service` above
— `analytics-service` degradation can never affect the booking path (NFR-8).

## Relationship to `auth-service`

**No synchronous call, but not "none" either.** `booking-service` never calls `auth-service` at
request time — per `docs/architecture/authentication-flow.md`'s "Service-to-Service Calls" and
`auth-service`'s own `security-design.md` "Defense in Depth: Gateway vs. Service," a downstream
service independently validates the JWT's signature (against `auth-service`'s published public
key) and reads its claims locally; it never phones `auth-service` to ask whether a token is valid.
`booking-service` follows this exactly: `api-gateway` authenticates the request and forwards
identity context; `booking-service` independently checks the role claim against the specific
action being attempted, and checks the subject claim against booking ownership. See "Booking ↔
Auth" detail below.

## Booking ↔ Auth: Ownership Enforcement

Every client-facing operation in this service requires an authenticated identity
(`docs/architecture/authentication-flow.md`, NFR-11). `booking-service` enforces two distinct
things, independently, per `auth-service`'s own defense-in-depth model:

- **Authentication** — already done by `api-gateway`; `booking-service` trusts the propagated
  identity context but re-validates the token's signature/expiry at its own boundary rather than
  assuming the gateway's check was sufficient (`security-design.md`'s stated rule, applied here).
- **Authorization** — role- and ownership-based, decided entirely within `booking-service`, using
  only its own data:
  - **`TRAVELER`** — may create a booking, view/cancel/download-ticket only for bookings where
    `Booking.travelerId` equals the token's subject claim. Attempting to access another
    traveler's booking is a `404`, not a `403` — the same enumeration-protection posture
    `auth-service`'s own `security-design.md` applies to login/password-reset, so a traveler
    cannot distinguish "not yours" from "doesn't exist."
  - **`OPERATOR`** — may view (read-only) bookings against trips their own operator account
    owns (`FR-5.5`, `api-inventory.md`'s "operator-portal (view own trip's bookings)"). Verifying
    that a given trip belongs to the requesting operator is itself a dependency on
    `operator-service` (not yet built) — flagged as part of the same gap noted above under
    "Relationship to `operator-service`," not a new one.
  - **`ADMIN`/`SUPPORT`** — may view any booking, unrestricted, backing FR-8.3's support-lookup
    journey (`docs/requirements/user-journeys.md` §6). Never granted write access to booking state
    beyond what a `TRAVELER` could already do to their own booking — support resolves issues by
    triggering the same operations a traveler could trigger, not by a separate privileged mutation
    path, keeping the state machine's entry points to a minimum.

## What's Deliberately Out of Scope

- **Any provider-specific logic, HTTP client, or resilience policy** — see "Non-Responsibilities"
  in `responsibilities.md`.
- **Catalog aggregation, synchronization, or geography** — `inventory-service`'s, unchanged by
  this specification.
- **A saga/outbox implementation's exact mechanics** — `docs/architecture/high-level-design.md`
  §6 names the pattern (transactional outbox, per-service) as the target for the
  `booking-service` ↔ `payment-service` path; the exact outbox table shape, polling/publishing
  mechanism, and failure-retry behavior are implementation decisions, not fixed here, the same way
  every other service's Flyway schema and adapter internals are left to implementation across this
  documentation set.
- **Cross-vertical booking** (train tickets, flight bookings) — Phase 2+, per
  `docs/architecture/high-level-design.md` §12. Nothing in this specification assumes anything
  bus-specific beyond what `inventory-service`'s own catalog model already assumes.
