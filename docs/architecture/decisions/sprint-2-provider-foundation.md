# Sprint 2 — Provider Foundation + Google Places (implementation plan)

**Status:** approved 2026-07-29 · supersedes the original single-service sprint brief

## The correction this plan records

The Sprint 2 brief specified a `provider/` module inside **search-service**, with new `provider`,
`provider_credentials` and `provider_runtime` tables.

Implementing that literally would have created the platform's **second provider registry**.
`provider-integration-service` already owns:

| Sprint 2 brief asked for | Already present |
|---|---|
| `provider` table | `provider_configurations` — FLIXBUS already seeded (V5) |
| `provider_runtime.session_token / session_expiry` | `provider_sessions` + `POST /sessions/{id}/refresh` |
| `provider_runtime.health_status / last_*_call` | `provider_health` + `GET /providers/{type}/health` |
| `Provider` aggregate | `domain/model/Provider.java` |
| `supports_search`, `supports_seat_map`, … | `ProviderCapability` (7 values, stored in `capabilities`) |
| `TestProviderConnection` | `CheckProviderHealth` |
| `RefreshProviderSession` | `RefreshSession` |

`docs/architecture/service-boundaries.md` designates `provider-integration-service` "the only
service allowed to call a provider directly", because a provider detail living anywhere else
becomes a second place it must be maintained. Two registries would also give two answers to "is
FLIXBUS enabled" — an operational hazard, not merely duplication.

**Decision: extend the existing service; do not introduce a second registry anywhere.**

## Split of work

### search-service — Google Places enrichment

Location remains the canonical location domain. `provider_location_mapping` stays here.

- `GooglePlacesProperties` / `GooglePlacesConfiguration` — API key from `GOOGLE_PLACES_API_KEY`,
  never reaching the browser: the key is used server-side only and no endpoint echoes it.
- `GooglePlacesClient` (outbound port) + `GooglePlacesAdapter` (RestClient implementation),
  following the `InventoryAvailabilityClientAdapter` shape already used here.
- Autocomplete cache: configurable TTL and max size; **caches successes only**. A failed or
  degraded lookup is never stored, so an outage cannot be frozen into the cache for its whole TTL.
- `GET /api/v1/google/places?q=` — **public**, matching the existing anonymous read surface.

**Google Places enriches RoadScanner locations only. It never creates a provider mapping.** The
suggestion payload carries Google's own place identifiers, not RoadScanner location ids, because a
suggestion is a *candidate* — promoting one into the catalogue stays the existing admin
`POST /api/v1/locations` path, which is where `googlePlaceId` uniqueness is already enforced.

### provider-integration-service — provider foundation

Extend, reusing `Provider`, `ProviderCapability`, `ProviderSession`, `ProviderHealth` as they are.

**Schema (`V6__provider_foundation.sql`)**

- New `provider_credentials` table (FK → `provider_configurations`), holding
  `partner_email` / `partner_password` / `partner_token` with an `encrypted` flag.
- `provider_configurations` gains `timeout_ms` and `retry_count`.
- `provider_configurations` gains `provider_category` (`BUS` / `RAIL` / `AIRLINE` / …).

Two deliberate deviations from the brief's column list, both to avoid redundancy:

1. **No separate `code` column.** The brief had both `code` (FLIXBUS) and `provider_type`
   (the category). Here the existing `provider_configurations.provider_type` *is* the code — it is
   already `UNIQUE`, already the `ProviderType` value object, and already normalised upper-case.
   Adding `code` would duplicate it and invite the two to disagree.
2. **The brief's `provider_type` becomes `provider_category`**, carrying its actual intent — the
   vertical a provider belongs to. This is what lets Rail and Airline providers land later without
   redesign, which was the brief's stated goal for that column.

`supports_*` booleans are **not** added: `ProviderCapability` already models exactly these, is
already persisted in `capabilities`, and is already what `GetProviderCapabilities` reads. Six
boolean columns would be a second, drifting source of the same truth.

**Domain**

- New `ProviderCredentials` aggregate + `ProviderCredentialsRepository` port.
- `Provider` gains `timeoutMillis`, `retryCount`, `category`, and mutators for the admin edits.
- `ProviderConfigurationRepository` gains `findAll()`, `findById()` and `save()`. Its Javadoc
  currently states providers are never created through this service's API — that rule is being
  deliberately reversed by this sprint, so the Javadoc is updated rather than left contradicting
  the code.

**Application** — `CreateProvider`, `UpdateProvider`, `EnableProvider`, `DisableProvider`,
`ListProviders`, `GetProvider`. `TestProviderConnection` and `RefreshProviderSession` are thin
adapters over the existing `CheckProviderHealth` and `RefreshSession` rather than new logic.

**REST** — `/api/v1/providers` (admin, `ROLE_ADMIN`): list, get, create, update,
enable, disable, test, refresh-session. The existing `/internal/api/v1/...` surface is untouched.

**Security** — this service currently has no `SecurityFilterChain`. It gains the same JWT
resource-server setup search-service received in Sprint 1's hardening (`JwtConfig`,
`SecurityConfig`, `JwtVerificationProperties`, ephemeral keys for local/test). The existing
`/internal/**` surface stays permitted, matching its documented private-network posture; only the
new `/api/v1/providers` surface requires `ROLE_ADMIN`.

## Explicitly out of scope

No provider search, seat maps, booking, checkout, payments, Kafka changes, polling, schedulers,
city synchronisation, or automatic provider mapping. Provider mappings continue to be created
manually / by seed / by import — business logic only ever *queries* them, so how a mapping got
there stays irrelevant to every consumer.

## Verification

`mvn clean verify` in both services · Flyway applies on a real Postgres · Hibernate
`ddl-auto: validate` passes · OpenAPI documents the new admin routes · existing integration and
end-to-end tests still green · both services boot under Docker.

---

## Implementation notes (as built)

Three deviations from the brief's literal text, each recorded here rather than left as a surprise:

1. **FLIXBUS stays disabled.** The brief said `enabled = true`. Its `base_url` is still the
   placeholder `flixbus.example.invalid`, so enabling it would point this service's health probe at
   a host that cannot resolve, and would make the provider eligible for real traffic before any
   credential exists. It is enabled at runtime via `POST /api/v1/providers/{id}/enable` — which is
   what this sprint's admin API is for.

2. **No `code` column; `provider_category` instead of a second `provider_type`.** See the schema
   section above. `provider_configurations.provider_type` already is the unique code.

3. **No `supports_*` booleans.** `ProviderCapability` already models them and is already what
   `GetProviderCapabilities` reads.

### Known gap: credentials are not encrypted at rest

`provider_credentials` stores `partner_password` and `partner_token` in plaintext. The `encrypted`
column records intent per row; nothing in Sprint 2 encrypts or decrypts. Mitigations in place:

- The values are **write-only over HTTP**. `PUT /providers/{id}/credentials` accepts them;
  no endpoint returns them. `ProviderCredentialsResponse` has no field that could hold one.
- `ProviderCredentials.toString()` renders `password=set|absent`, never a value.
- `ProviderCredentialsRepository` has no `findAll()` — nothing can enumerate every secret.

Until a KMS-backed attribute converter exists, database access must be restricted accordingly.
That converter, plus a backfill that flips `encrypted` per row, is the natural follow-up.
