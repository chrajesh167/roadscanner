# Inventory Service — Events Published

**All catalog events, nothing dynamic.** Per the architecture review, `SeatHeld`/`SeatReleased`
(the old version of this document's entries) have been removed entirely — they now belong to
`provider-integration-service`, and are not this service's events under any name.

| Event | Consumers | Purpose |
|---|---|---|
| `TripPublished` | `search-service` | A trip (first-party or provider-synced) enters the merged catalog |
| `TripUpdated` | `search-service` | Fare, schedule, or route changed on an existing catalog trip |
| `TripCancelled` | `search-service`, `booking-service` | A catalog trip is no longer bookable |
| `RouteUpdated` | `search-service` (if it chooses to consume it — currently it doesn't need route-level granularity beyond what's embedded in trip events) | A route's shape changed |
| `OperatorUpdated` | `search-service` | An operator's display fields changed |
| `FareSnapshotUpdated` | `search-service` | A fare changed independent of a broader trip update (e.g., a provider-side price change discovered by sync) |
| `CatalogSyncCompleted` / `CatalogSyncFailed` | `analytics-service` | Observability into provider catalog synchronization health |

## Same Event Names, Different Producer, Different Topic — Why That's Correct

`operator-service` still publishes its own `TripPublished`/`TripUpdated`/`TripCancelled` on its
own topic, describing its own first-party facts — that producer relationship is unchanged (see
`events-consumed.md`). `inventory-service` publishes events with the **same names** on **its own,
separate topic**, describing the merged catalog view after both ingestion paths (first-party
events, provider-synced reconciliation) have been applied. Per
`docs/architecture/event-catalog.md`'s "every event has one publisher" rule, this is satisfied
per-topic, not per-name — the two `TripPublished`s are different events that happen to share a
name because they mean the same *kind* of thing at two different points in the pipeline, the same
way `BookingCreated` and `BookingConfirmed` are both booking-lifecycle events without being the
same event.

**Why `search-service` must consume the `inventory-service` topic, not `operator-service`'s
directly:** `operator-service` only ever knows about first-party operators. If `search-service`
kept consuming from `operator-service`, every third-party-provider trip (FlixBus, RedBus, ...)
would never appear in search results — silently defeating a large part of why
`provider-integration-service` exists. This is the one required change to
`docs/services/search-service/events-consumed.md`, and it is a Kafka topic-source change only —
the event payload shape is unchanged, so no code in `search-service` needs to change, only which
topic its consumer is configured to read.

## Why There's No `SeatBlocked`/`SeatReleased`/`SeatAllocated` Here

None of that is this service's fact to report — it never happens here. See
`docs/services/provider-integration-service/events-published.md` (updated by this review) for
where those events now live.
