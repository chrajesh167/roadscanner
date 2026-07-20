# Use Cases

One inbound port per use case (`domain/port/in`), each implemented by a single application-layer
service (`application/usecase/<feature>`).

| Use Case | Port | Notes |
|---|---|---|
| Authenticate Provider | `AuthenticateProvider` | Opens a session; requires the provider to be both configured and `enabled` |
| Refresh Session | `RefreshSession` | Exchanges a still-`ACTIVE` session's token for a fresh one |
| Search Trips | `SearchTrips` | Searches the provider bound to an existing session |
| Get Seat Map | `GetSeatMap` | Read-through against a short-TTL Redis cache |
| Block Seat | `BlockSeat` | Places a temporary hold with the provider |
| Release Seat | `ReleaseSeat` | Idempotent release of a hold |
| Confirm Booking | `ConfirmBooking` | Converts a still-blocked hold into a confirmed booking |
| Download Ticket | `DownloadTicket` | Fetches the ticket document for a confirmed booking |
| Get Provider Capabilities | `GetProviderCapabilities` | Intersection of configured and adapter-implemented capabilities |
| Check Provider Health | `CheckProviderHealth` | Live probe; records + conditionally publishes an audit event; shared by the REST endpoint and the scheduled monitor |

## Supporting, Non-Port Application Logic

- **`ActiveSessionResolver`** — shared session-validity check used by every session-scoped use
  case above (all but capability discovery and health check).
- **`AuditRecorder`** — the "write Postgres, then publish Kafka" sequencing shared by
  `CheckProviderHealthService` and `SessionExpirySweeper`.
- **`SessionExpirySweeper`** — marks stale sessions `EXPIRED`, evicts their cache entry, publishes
  `SessionExpired`. Driven on a schedule.
- **`ProviderHealthMonitor`** — drives `CheckProviderHealth` for every enabled provider on a
  schedule.
