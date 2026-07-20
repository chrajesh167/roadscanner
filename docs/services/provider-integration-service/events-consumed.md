# Events Consumed

**None.** This service has no Kafka listener of any kind.

This is a deliberate design outcome, not an oversight: `provider-integration-service` is a
synchronous integration/resilience boundary, not a read-model aggregator (contrast with
`search-service`, which is built almost entirely around consuming events to keep its index
current — see that service's identically-titled `events-consumed.md`). Every input this service
needs — which provider to talk to, what to search for, which seats to block — arrives
synchronously, as part of the REST request driving the use case. There is no upstream event
stream this service's own state (sessions, health, audit trail) is derived from; it is derived
entirely from this service's own actions.
