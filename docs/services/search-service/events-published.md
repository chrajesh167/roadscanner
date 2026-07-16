# Search Service — Events Published

## None

`search-service` publishes no Kafka events, in either direction — confirmed against `docs/architecture/event-catalog.md`, which lists no row with `search-service` as a producer.

This mirrors `docs/services/auth-service/overview.md`'s equivalent statement about `auth-service` having no event surface, for a related but distinct reason:

- **`auth-service` has no events** because its operations are inherently synchronous, latency-sensitive request/response (login, refresh) with nothing to usefully decouple.
- **`search-service` has no events** because nothing on the platform depends on knowing its index changed. `search-service`'s state is a derived *effect* of other services' events (`events-consumed.md`), never a *cause* anything downstream needs to react to. A traveler's next action (searching again, or holding a seat via `inventory-service`) is satisfied by a fresh query, not by a notification that the index updated.

## Why This Is Correct, Not an Oversight

Introducing an event here — e.g., a hypothetical `SearchIndexUpdated` — would have no consumer and no purpose; per `docs/architecture/database-ownership.md`'s reasoning for the read-model pattern, the value of an event is that some other service needs to react to a fact. No service needs to react to `search-service`'s internal bookkeeping. This is worth stating explicitly (rather than leaving the section blank) precisely because it's easy to assume every service must both produce and consume in an event-driven platform — `search-service` is the clearest counterexample on this platform: a pure sink for events, and a pure source of nothing.

## The One Thing That Looks Like an Event But Isn't

The synchronous, cached call to `inventory-service` for live availability (`boundaries.md`, `sequence-diagrams.md` §4) is a request/response read, not an event — it carries no state change and nothing is "published." It's mentioned here only to preempt the natural question of whether it belongs in this document; it doesn't.
