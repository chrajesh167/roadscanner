# Domain Model

## Persisted Aggregates

- **`Provider`** — a configured provider: type, display name, enabled flag, declared
  capabilities, base config. Managed exclusively via Flyway seed migrations (see
  `README.md`'s "How to Add a New Provider") — never created or updated through this service's
  own API.
- **`ProviderSession`** — a live authenticated session against one provider. Two idempotent state
  transitions (`expire`, `revoke`), both terminal — a session never returns to `ACTIVE` once
  either fires, matching `auth-service`'s `RefreshToken.revoke` and `search-service`'s
  `SearchableTrip.cancel` pattern.
- **`AuditRecord`** — one durable entry per `ProviderUnavailable`/`ProviderRecovered`/
  `SessionExpired` event, insert-only.
- **`ProviderHealth`** — one row per `ProviderType`, tracking current state and consecutive
  failure count, updated by every health check (scheduled or on-demand).

## Value Objects

- **`ProviderType`** — an open value object (normalized code string), not a closed `enum` — the
  entire mechanism behind "add a provider without changing business logic." See
  `ProviderClientRegistry`'s Javadoc.
- **`ProviderCapability`** — closed `enum` (`SEARCH`, `SEAT_MAP`, `SEAT_BLOCK`, `SEAT_RELEASE`,
  `BOOKING_CONFIRMATION`, `TICKET_DOWNLOAD`, `HEALTH_CHECK`) — this vocabulary is platform-defined
  and never grows when a provider is added, only when this service's own feature set does.
- **`ProviderToken`**, **`SearchCriteria`**, **`FareAmount`**, **`SeatNumber`**,
  **`PassengerDetail`**, **`ProviderHealthCheck`**, **`ProviderError`** — plain, validated carriers
  with no persistence of their own.

## Pass-Through Domain Objects (Never Persisted)

- **`ProviderTrip`**, **`ProviderSeatMap`** (+ `ProviderSeat`) — search/seat-map results, mapped
  from the provider's own response shape by that provider's adapter (e.g. `FlixBusMapper`).
  `ProviderSeatMap` is short-TTL cached in Redis as a read-through optimization only.
- **`SeatReservation`** — the result of a seat block. Carries its own lifecycle methods
  (`release()`, `confirm()`, `isExpired()`) for the *caller's* convenience when composing a flow,
  but this service itself never stores an instance between calls — see `boundaries.md`.
- **`BookingConfirmation`**, **`ProviderTicket`** — the results of confirming a booking and
  downloading its ticket, respectively. Same non-persistence rationale.

## Exception Hierarchy

Every provider-specific failure — an HTTP status, a timeout, a malformed response — is translated
at the adapter boundary (e.g. `FlixBusExceptionTranslator`) into one of:
`ProviderNotSupportedException`, `ProviderAuthenticationException`, `SessionExpiredException`,
`SeatUnavailableException`, `BookingFailedException`, `ProviderUnavailableException`,
`TicketNotFoundException`, `ProviderTripNotFoundException` — all extending
`ProviderIntegrationException`, which carries a canonical `ProviderError` (code, message,
retryability). Application and REST-layer code never sees a provider-specific exception type.
