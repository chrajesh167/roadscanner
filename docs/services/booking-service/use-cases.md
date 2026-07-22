# Booking Service — Use Cases

Four kinds of use case: **client-facing** (via `api-gateway`), **service-to-service** (a direct
call from another backend service, no gateway), **event-driven** (Kafka), and
**scheduled/operational**. See `domain-model.md` for the shapes these operate on and
`sequence-diagrams.md` for the full call sequences.

## Client-Facing

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Get Seat Selection View** | Traveler viewing seats for a trip (FR-2.4, FR-3.1) | Call `inventory-service` for `Trip` metadata, `SeatLayout`, and `ProviderMapping`; call `provider-integration-service` (authenticate/reuse session, `GetSeatMap`) for live per-seat status; compose the two into one response | `inventory-service`'s own `sequence-diagrams.md` flow 4 explicitly assigns this composition to `booking-service`, not to `inventory-service` or the client directly |
| **Hold Seats** | Traveler selects specific seat(s) and proceeds (FR-3.1, FR-3.2) | Re-validate the trip against `inventory-service` (catching a `TripCancelled` that happened since the seat-selection view was shown); call `provider-integration-service`'s `BlockSeat` for the selected seat numbers; persist a `SeatHold` (traveler, trip, provider identity, block reference, seat numbers, `expiresAt`); return the hold reference and `expiresAt` to the client | If the trip has no `ProviderMapping`, this fails validation immediately — see "A Trip With No `ProviderMapping` Cannot Be Held" below. If `BlockSeat` reports the seat unavailable, this fails with that outcome, no `SeatHold` created |
| **Release Hold** | Traveler abandons checkout before paying (FR-3.4's explicit-release path) | Call `provider-integration-service`'s `ReleaseSeat` for the hold's `providerBlockReference`; delete the local `SeatHold` | Idempotent — releasing an already-released or already-expired hold is a no-op, matching `ReleaseSeat`'s own idempotency (`docs/services/provider-integration-service/api-summary.md`) |
| **Create Booking** | Traveler submits passenger details against a held reference (FR-3.1) | Look up the `SeatHold` by its reference and traveler; check `expiresAt` against the current clock (the local-check resolution to `boundaries.md`'s "Known Gap: No Read-Only Reservation-Status Check"); if still valid, persist a `Booking` in `PENDING_PAYMENT` with the passenger details, fare snapshot, and provider identity carried over from the hold; consume the `SeatHold`; publish `BookingCreated` | A hold token becomes **at most one** booking (`docs/architecture/booking-flow.md`'s idempotency requirement) — see `domain-model.md`'s invariants. An expired hold fails this use case with a clear "hold expired, re-select seats" outcome, matching FR-3.4's stated failure path (`docs/requirements/user-journeys.md` §1's "Seat hold expires before payment → traveler must re-select seats") |
| **Get Booking** | Traveler, operator, or admin/support views a booking | Return the `Booking` by id, after the ownership check in `boundaries.md`'s "Booking ↔ Auth" | Unauthorized access returns "not found," not "forbidden" — see that section |
| **List Booking History** | Traveler views their own bookings (FR-1.3); operator views bookings against their own trips (FR-5.5) | Return every `Booking` for the requesting traveler (all statuses, including `CANCELLED`/`COMPLETED`), or every `Booking` for a trip the requesting operator owns | Nothing is ever excluded by status — see `domain-model.md`'s "Soft Delete" |
| **Cancel Booking** | Traveler cancels a booking (FR-3.5) | If `PENDING_PAYMENT`: call `ReleaseSeat` for the held reservation, transition to `CANCELLED` (`TRAVELER_REQUESTED`), publish `BookingCancelled` — no refund action (payment never succeeded). If `CONFIRMED`: check cancellation policy (dependency on `operator-service` — see `boundaries.md`), transition to `CANCELLED` (`TRAVELER_REQUESTED`), request a refund, publish `BookingCancelled` — **no provider-side reversal is attempted**, per `boundaries.md`'s "Known Gap: Post-Confirmation Cancellation" | Idempotent — cancelling an already-`CANCELLED` booking is a no-op |
| **Get Ticket** | Traveler downloads their e-ticket (FR-3.6) | Return the `Ticket` already persisted on the `Booking` at confirmation time | Only valid for `CONFIRMED` (or later, `COMPLETED`) bookings — anything else returns "no ticket yet," not an error about the booking itself |

### A Trip With No `ProviderMapping` Cannot Be Held

`inventory-service`'s domain model states a `Trip` with no `ProviderMapping` is "a pure
first-party trip with no third-party equivalent — not an error state" at the catalog level. But
every provider operation `booking-service` needs (`BlockSeat`, `ConfirmBooking`, ...) requires a
`providerType` + `providerTripId`, which only exists via a `ProviderMapping`. **`Hold Seats`
therefore fails validation with a clear "this trip cannot currently be booked" outcome for any
trip with no `ProviderMapping`.** This is a real, currently-unclosed platform gap — first-party
(`operator-service`-sourced) trips are fully searchable and viewable, but not yet bookable, until
a first-party live-booking mechanism exists (`docs/architecture/seat-locking-flow.md`'s "future
first-party/native supply path... not present today"). Flagged in `overview.md`'s ambiguity #2,
not resolved further here — resolving it would mean designing a live-booking mechanism for
first-party supply, which is out of `booking-service`'s scope and depends on decisions
`inventory-service`'s and `operator-service`'s own architecture would need to make first.

## Service-to-Service (No Gateway)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Verify Booking** | `review-service` (not yet built), validating FR-7.2 before accepting a review submission | Given a traveler id and trip id, return whether a `COMPLETED` booking exists for that pair | The only inbound service-to-service call any other service makes against `booking-service` — see `boundaries.md`'s "Relationship to `review-service`" |

## Event-Driven

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Handle Payment Completed** | `PaymentCompleted` (from `payment-service`, not yet built) | For the referenced `PENDING_PAYMENT` booking: call `provider-integration-service`'s `ConfirmBooking` with the held reservation and passenger list; on success, transition to `CONFIRMED`, persist the returned `providerBookingReference` and `Ticket` (via `DownloadTicket`), publish `BookingConfirmed`. On `ConfirmBooking` failure: transition to `CANCELLED` (`PROVIDER_CONFIRMATION_FAILED`), set `supportFlagged = true`, request an automatic refund, publish `BookingCancelled` | This is `docs/architecture/booking-flow.md`'s flagged edge case ("provider confirmation fails after payment already succeeded") — see `domain-model.md`'s `supportFlagged` field. A `PaymentCompleted` for an already-`CANCELLED` booking (the late-success case) is handled the same way — refund plus flag, never a silent re-confirmation |
| **Handle Payment Failed** | `PaymentFailed` (from `payment-service`) | Transition the `PENDING_PAYMENT` booking to `CANCELLED` (`PAYMENT_FAILED`); release the held reservation if not already released; publish `BookingCancelled` | No refund action — payment never succeeded |
| **Handle Payment Timed Out** | `PaymentTimedOut` (from `payment-service`) | Same as "Handle Payment Failed," reason `PAYMENT_TIMED_OUT` | The distinct reason is preserved for reconciliation even though the traveler-facing outcome is identical, matching `docs/architecture/payment-flow.md`'s own rationale for keeping `TIMED_OUT` distinct from `FAILED` |
| **Handle Seat Released** | `SeatReleased` (from `provider-integration-service` — **specified, not yet published**, see `events-consumed.md`) | If a `PENDING_PAYMENT` booking or an outstanding `SeatHold` references the released reservation, transition/discard it (`CANCELLED`/`HOLD_EXPIRED`, or delete the `SeatHold`) | Covers the case where a hold expires before the traveler ever reaches `payment-service` — no `PaymentFailed`/`PaymentTimedOut` will ever arrive for a payment attempt that never started. Must treat a second delivery for an already-handled release as a no-op — `docs/architecture/event-catalog.md`'s stated failure mode for this event |
| **Handle Trip Cancelled** | `TripCancelled` (from `inventory-service`) | For every `CONFIRMED` booking against the cancelled trip: transition to `CANCELLED` (`TRIP_CANCELLED`), request a **full refund regardless of the normal cancellation-fee policy**, publish `BookingCancelled` — **no provider-side reversal attempted**, same gap as traveler-initiated post-confirmation cancellation | `docs/architecture/booking-flow.md` step 7's explicit business rule: the traveler didn't cause this, so the standard fee schedule doesn't apply. Also applies to any `PENDING_PAYMENT` booking against the trip — release the hold, cancel, `TRIP_CANCELLED` reason, no refund needed |

## Scheduled / Operational

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Complete Booking** | Scheduled sweep (`docs/requirements/actors.md`'s Scheduler/System Jobs actor: "closing out bookings after departure") | For every `CONFIRMED` booking whose trip's departure time has passed, transition to `COMPLETED` | Backs FR-7.2's review-eligibility gate. Needs the trip's departure time — re-read from `inventory-service` at sweep time, or captured once at confirmation time; an implementation decision, not fixed here |
| **Sweep Stale Holds** *(defensive, secondary to event-driven handling)* | Scheduled, low-frequency | Delete any `SeatHold` past its `expiresAt` that "Handle Seat Released" hasn't already cleaned up | A safety net only, given `SeatReleased` is not yet published by `provider-integration-service` (`events-consumed.md`) — without this sweep, an abandoned `SeatHold` would otherwise sit until the traveler or an operator notices. Not needed for `Booking` correctness (an expired hold simply fails "Create Booking" on its own, per that use case's row above), only for `SeatHold` table hygiene |

## What's Deliberately Not a Use Case Here

- **Placing or releasing a lock/hold as an owned mechanism** — every hold operation is a relay to
  `provider-integration-service`; `booking-service` never implements locking itself.
- **Any catalog browsing, city/station/route lookup, or trip search** — `inventory-service`'s and
  `search-service`'s, respectively. `booking-service` only ever asks `inventory-service` about one
  specific trip it already has an id for.
- **Payment initiation, gateway integration, or refund execution** — `payment-service`'s. This
  service only *requests* a refund and reacts to payment outcomes; it never talks to a gateway.
- **Notification dispatch of any kind** — `notification-service` reacts to the events this service
  publishes; `booking-service` never sends an email, SMS, or push notification itself.
- **Cancellation-policy computation** — `operator-service`'s configuration, once that service
  exists (`boundaries.md`).
