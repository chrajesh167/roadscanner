# Inventory Service — Sequence Diagrams

Five flows: first-party ingestion, provider catalog synchronization, the availability facade
(what `search-service` actually triggers today), and the two booking-time flows that replace the
old (incorrect) direct-hold-against-inventory design.

## 1. First-Party Trip Published → Catalog Updated

```mermaid
sequenceDiagram
    participant OS as operator-service
    participant K as Kafka
    participant IS as inventory-service

    OS->>K: TripPublished (operator-service topic)
    K->>IS: deliver (at-least-once)
    IS->>IS: upsert Trip (FIRST_PARTY), TripSchedule, SeatLayout
    IS->>K: publish TripPublished (inventory-service topic — the merged catalog view)
```

Two different topics, two different producers, same event name by design — see
`events-published.md` for why this isn't a naming collision in practice.

## 2. Scheduled Provider Catalog Synchronization

```mermaid
sequenceDiagram
    participant Sched as Scheduler
    participant IS as inventory-service
    participant PIS as provider-integration-service
    participant K as Kafka

    loop per enabled provider
        Sched->>IS: synchronize(providerType)
        IS->>PIS: authenticate (or reuse session)
        loop per known route/date window
            IS->>PIS: search(route, date)
            PIS-->>IS: provider trips
            IS->>IS: reconcile into Trip / SeatLayout / FareSnapshot / ProviderMapping
        end
        IS->>IS: record SyncRecord
        IS->>K: publish CatalogSyncCompleted (or Failed)
        IS->>K: publish TripPublished/TripUpdated for any new/changed trips
    end
```

A failure against one provider does not stop synchronization for the others — same
failure-isolation principle `provider-integration-service`'s own `ProviderHealthMonitor` already
applies.

## 3. Search Service Queries Availability (the facade — contract unchanged)

```mermaid
sequenceDiagram
    participant SS as search-service
    participant IS as inventory-service
    participant PIS as provider-integration-service

    SS->>IS: GET /trips/{tripId}/availability
    IS->>IS: resolve ProviderMapping for tripId
    alt mapping exists
        IS->>PIS: live seat-map / search (authenticate or reuse session)
        PIS-->>IS: live seat state
        IS-->>SS: {"availableSeats": <int>}
    else provider unreachable, or no mapping
        IS-->>SS: error response
        Note over SS: search-service's existing AvailabilityClient already<br/>treats this as "unknown" — no change needed on that side
    end
```

This is the diagram that proves point 4 of the review: `search-service` issues the exact same
call it issues today, gets the exact same response shape, and never learns that a provider is
involved at all.

## 4. Booking Service Composes the Seat-Selection View

```mermaid
sequenceDiagram
    participant T as Traveler
    participant BS as booking-service
    participant IS as inventory-service
    participant PIS as provider-integration-service

    T->>BS: view seats for trip
    BS->>IS: get Trip metadata + SeatLayout + ProviderMapping
    IS-->>BS: catalog shape
    BS->>PIS: authenticate (or reuse session) for providerType
    BS->>PIS: get live seat map for providerTripId
    PIS-->>BS: live per-seat status
    BS->>BS: compose SeatLayout shape + live status
    BS-->>T: unified seat-selection view
```

`inventory-service` is not in this diagram after the first call — it has nothing further to
contribute once catalog shape is handed over.

## 5. Booking Service Creates a Seat Hold (replaces the old, incorrect direct-to-inventory flow)

```mermaid
sequenceDiagram
    participant T as Traveler
    participant BS as booking-service
    participant PIS as provider-integration-service

    T->>BS: select seat(s), proceed
    BS->>PIS: block seat(s) for the ProviderMapping already resolved in flow 4
    PIS-->>BS: reservation reference
    BS-->>T: hold confirmed
```

`inventory-service` does not appear here at all — it already did its job in flow 4. This is the
direct replacement for the previous version of this document's "Traveler Creates a Seat Hold"
diagram, which incorrectly placed `inventory-service` where `provider-integration-service` now
sits. See `docs/architecture/booking-flow.md` (corrected by this review) for how this fits into
the full booking lifecycle, including payment and confirmation.
