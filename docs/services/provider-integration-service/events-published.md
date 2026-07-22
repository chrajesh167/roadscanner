# Events Published

Topic: `provider-integration-events` (single topic, discriminated by `eventType`, keyed by
`providerType` — matching `search-service`'s `TripEventMessage`/`TripEventType` single-topic
precedent, mirrored here as `ProviderAuditMessage`).

| Event | Trigger | Purpose |
|---|---|---|
| `ProviderUnavailable` | A provider's health state transitions into `UNAVAILABLE` from anything else | Signals downstream services to degrade gracefully for that provider |
| `ProviderRecovered` | A provider's health state transitions from `UNAVAILABLE` back to `HEALTHY` | Signals downstream services the provider is usable again |
| `SessionExpired` | A scheduled sweep marks an `ACTIVE` session `EXPIRED` (token past expiry, never refreshed) | Observability into session churn; not currently required for correctness by any consumer |
| `SeatBlocked` *(not yet implemented — gap identified by `docs/services/inventory-service/` architecture review, 2026-07-22)* | A `BlockSeat` call succeeds | Analytics/observability; assigned to this service (not `inventory-service`) because this is where the reservation actually happens |
| `SeatReleased` *(not yet implemented — same review)* | A reservation ends without becoming a confirmed booking — explicit release or TTL expiry | Consumed by `booking-service`, `analytics-service` — see `docs/architecture/event-catalog.md` |

`SeatBlocked`/`SeatReleased` are specified in `docs/architecture/event-catalog.md` (renamed from
the pre-`provider-integration-service` `SeatHeld`/`SeatReleased` design that was previously,
incorrectly, attributed to `inventory-service`) but are **not yet published by this service's
current implementation** — `AuditPublisher`/`KafkaAuditPublisherAdapter` today carry only the three
rows above. Adding them is a straightforward extension of the existing `AuditRecorder` pattern
(`BlockSeatService`/`ReleaseSeatService` would call it, the same way `CheckProviderHealthService`
already does) — flagged here as required before `booking-service` can rely on them, not designed
further in this pass.

## Likely Future Consumers (Not Implemented Anywhere Yet)

`booking-service` and `inventory-service` are the natural consumers of `ProviderUnavailable`/
`ProviderRecovered` — to pause/resume routing traffic to a specific provider without polling its
health endpoint on every request. `analytics-service` would consume all three for observability
and reporting, the same role it plays for every other event in the platform
(`docs/architecture/event-catalog.md`). None of these services exist yet, so no consumption logic
exists anywhere in the platform today — the events are produced, tested, and ready to be consumed
the moment a consumer does.

## Delivery Model

Same as every other event in this platform (`docs/architecture/event-catalog.md`): at-least-once,
partitioned by `providerType` for per-provider ordering, no cross-provider ordering guarantee. A
future consumer must be idempotent — a repeated `ProviderRecovered` for an already-healthy
provider should be a no-op, not an error.
