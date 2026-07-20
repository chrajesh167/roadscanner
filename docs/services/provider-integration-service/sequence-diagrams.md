# Sequence Diagrams

## Full Provider Interaction (Mock or FlixBus — identical shape either way)

```mermaid
sequenceDiagram
    participant Caller as booking-service / inventory-service / search-service
    participant PIS as provider-integration-service
    participant Cache as Redis
    participant DB as Postgres
    participant Provider as FlixBus / Mock

    Caller->>PIS: POST /sessions (authenticate)
    PIS->>Provider: authenticate()
    Provider-->>PIS: token
    PIS->>DB: save ProviderSession
    PIS->>Cache: cache token
    PIS-->>Caller: sessionId, expiresAt

    Caller->>PIS: GET /sessions/{id}/trips (search)
    PIS->>DB: load session, validate ACTIVE + not expired
    PIS->>Provider: search()
    Provider-->>PIS: trips
    PIS-->>Caller: trips

    Caller->>PIS: GET .../seat-map
    PIS->>Cache: check cache
    alt cache miss
        PIS->>Provider: getSeatMap()
        Provider-->>PIS: seat map
        PIS->>Cache: cache (short TTL)
    end
    PIS-->>Caller: seat map

    Caller->>PIS: POST .../seat-blocks (block)
    PIS->>Provider: blockSeats()
    Provider-->>PIS: reservation (providerBlockReference)
    PIS-->>Caller: reservation

    Caller->>PIS: POST .../booking (confirm)
    PIS->>Provider: confirmBooking()
    Provider-->>PIS: booking confirmation
    PIS-->>Caller: booking confirmation

    Caller->>PIS: GET .../ticket
    PIS->>Provider: downloadTicket()
    Provider-->>PIS: ticket
    PIS-->>Caller: ticket
```

## Scheduled Health Monitoring

```mermaid
sequenceDiagram
    participant Scheduler as ProviderMaintenanceScheduler
    participant Monitor as ProviderHealthMonitor
    participant CheckHealth as CheckProviderHealthService
    participant Provider as FlixBus / Mock
    participant DB as Postgres
    participant Kafka as Kafka (provider-integration-events)

    loop every 30s
        Scheduler->>Monitor: checkAllEnabledProviders()
        loop each enabled provider
            Monitor->>CheckHealth: check(providerType)
            CheckHealth->>Provider: checkHealth()
            Provider-->>CheckHealth: ProviderHealthCheck
            CheckHealth->>DB: recordCheck(), save ProviderHealth
            alt state transitioned into/out of UNAVAILABLE
                CheckHealth->>DB: save AuditRecord
                CheckHealth->>Kafka: publish ProviderUnavailable / ProviderRecovered
            end
        end
    end
```

**Failure isolation:** one provider's probe throwing an unexpected exception does not stop the
sweep for the remaining providers — see `ProviderHealthMonitor`'s Javadoc.
