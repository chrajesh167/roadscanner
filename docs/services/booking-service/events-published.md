# Booking Service — Events Published

Topic: `booking-events` (single topic, discriminated by `eventType`, keyed by `bookingId` for
per-booking ordering) — matching the single-topic-per-domain convention `inventory-service`'s
`CatalogTripEventMessage`/`CatalogTripEventType` and `provider-integration-service`'s
`ProviderAuditMessage` already establish on this platform, applied here for consistency, not
independently reinvented.

| Event | Consumers | Purpose |
|---|---|---|
| `BookingCreated` | `analytics-service` | A booking entered `PENDING_PAYMENT` — funnel tracking (`docs/architecture/event-catalog.md`). Fired the instant a `Booking` row is persisted; the closest thing this platform has to a `CREATED` signal — see `domain-model.md`'s "Reconciling the Requested State Vocabulary" |
| `BookingConfirmed` | `notification-service`, `analytics-service` | Payment succeeded **and** the provider confirmed the reservation — the booking is now real. `notification-service` sends the confirmation (FR-6.1) |
| `BookingCancelled` | `payment-service`, `notification-service`, `analytics-service` | The booking did not, or no longer, results in a confirmed reservation — payment failure/timeout, hold expiry, provider confirmation failure, traveler cancellation, or trip cancellation. `payment-service` consumes this only when a refund is owed (`docs/architecture/event-catalog.md`'s stated failure consideration); `notification-service` sends a cancellation/refund confirmation (FR-6.2) |

## Payload Shape (Conceptual — Not an OpenAPI/Avro Schema Yet)

Every event on this topic carries at minimum: `eventType`, `bookingId`, `travelerId`, `tripId`,
`status`, `occurredAt`. `BookingCancelled` additionally carries `cancellationReason`. Exact wire
schema is this service's own implementation decision, not fixed here — matching
`docs/architecture/event-catalog.md`'s own stated scope ("exact payload schemas are not defined in
this document").

## Why the Provider-Side Action Happens Before the Event, Not After

Per `docs/architecture/event-catalog.md`'s explicit correction: neither `BookingConfirmed` nor
`BookingCancelled` is a *trigger* for further seat-state mutation — the provider-side
confirm/release call already happened synchronously, inside `booking-service`'s own
`PaymentCompleted`/`PaymentFailed` handling, **before** either event is published (see
`sequence-diagrams.md` flows 4–5). Both events are purely downstream notifications of an
already-settled fact. No consumer of either event is ever in a position to observe a booking as
`CONFIRMED` before its provider-side reservation was actually confirmed, or as `CANCELLED` before
its provider-side hold was actually released (where a release is possible at all — see
`boundaries.md`'s "Known Gap: Post-Confirmation Cancellation" for the one case where it isn't).

## Failure Considerations

Same general model as every event on this platform (`docs/architecture/event-catalog.md`):
at-least-once delivery, ordering guaranteed only within the `bookingId` partition. A consumer that
sees `BookingConfirmed` or `BookingCancelled` twice for the same booking must treat the repeat as
a no-op.

**`BookingCreated` carries no correctness weight** — it exists purely for `analytics-service`'s
funnel tracking. A lost or delayed delivery affects a dashboard, never a booking's own state.

**`BookingCancelled` is the one row worth calling paging-worthy on the consumer side once
`payment-service` exists** — a stuck consumer there directly delays refunds, the same reasoning
`docs/architecture/event-catalog.md` already applies to `inventory-service`'s `TripCancelled`.

## Outbox Pattern (Per `docs/architecture/high-level-design.md` §6)

`booking-service`'s Postgres write (the state transition) and its corresponding Kafka publish are
intended to commit atomically via a transactional outbox, the one place on this platform that
pattern is adopted — see `boundaries.md`'s "What's Deliberately Out of Scope" for why the exact
outbox mechanics are not fixed in this document. Until `payment-service` exists and the full saga
is formalized (`docs/architecture/payment-flow.md`'s own stated timing: *"that formalization
happens when `booking-service` and `payment-service` are actually implemented"*), this remains the
target design, not a claim that the saga is complete — `booking-service`'s own state transitions
(hold → booking → confirm/cancel) are internally consistent regardless, since they're a single
service's own database writes; the outbox specifically protects the boundary between this
service's write and its published event, and between this service and `payment-service` once both
exist.

## What's Deliberately Not Published Here

- **Any seat-state event** (`SeatBlocked`, `SeatReleased`) — those belong to
  `provider-integration-service`, which actually performs the hold/release
  (`docs/services/provider-integration-service/events-published.md`). `booking-service` triggers
  those actions but never republishes them under its own name.
- **Any catalog event** (`TripPublished`, `TripUpdated`, `TripCancelled`, ...) —
  `inventory-service`'s, unaffected by anything in this specification.
- **Any payment or refund event** — `payment-service`'s, once it exists.
- **A "booking viewed" or similar low-value telemetry event** — not requested by any functional
  requirement; if `analytics-service` needs view-level funnel data, that's a future addition to
  this document, not assumed here.
