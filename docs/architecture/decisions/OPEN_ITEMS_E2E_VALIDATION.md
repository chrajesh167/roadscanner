# Open Architecture Items — End-to-End Validation, 2026-07-28

Raised during the first full end-to-end run of the traveller booking flow
(register → search → seat selection → hold → booking → payment → capture → confirmation)
against locally running `auth`, `search`, `inventory`, `provider-integration`, `booking` and
`payment` services.

Startup and integration defects found in that run were fixed and committed separately. **The items
below were deliberately NOT fixed** — each is an architectural decision, not a code defect, and
picking a direction unilaterally would bake a choice into the platform that the team has not made.

---

## 1. `operatorId` contract break between inventory-service and search-service

**Status:** blocking — search-service cannot index any provider-sourced trip.

`inventory-service` deliberately creates provider-synced trips with **no operator id**:

> `Trip.createFromProviderSync(...)` — *"no `operatorId` (provider-sourced trips have no
> first-party operator account), only a display name copied from the provider's own response."*
> — `inventory-service/.../domain/model/Trip.java`

`search-service` requires it to be present, in two places:

- `OperatorId` is a value object with `Objects.requireNonNull(value, "value must not be null")`
- `searchable_trips.operator_id` is declared `UUID NOT NULL` in `V1__create_searchable_trips.sql`

`TripEventListener` calls `new OperatorId(message.operatorId())` unconditionally, so every
`TripPublished` event carrying a null operator id throws `NullPointerException`, is retried three
times, and is routed to the DLQ. **Because all Phase-1 supply is provider-synced (`MOCK`), the
search index stays empty.** This is why search returned no results before intervention.

### Options

| Option | Change | Consequence |
|---|---|---|
| **A. search-service tolerates a null operator id** | Nullable domain field + migration relaxing `NOT NULL`; `TripResponse.operatorId` becomes nullable | Honest to the catalog: provider supply genuinely has no first-party operator. Ripples into any consumer that assumed a non-null operator id, including the frontend's `TripResponse` type |
| **B. inventory-service attaches an operator identity to provider supply** | Mint or resolve a stable operator id for provider-sourced trips | Keeps every downstream contract unchanged, but invents identity data and contradicts `Trip.java`'s stated intent |
| **C. Operator accounts become mandatory for all supply** | `operator-service` onboards an operator record per provider; sync links to it | Cleanest long-term model, largest scope, depends on `operator-service` which is not implemented |

### Recommendation

**Do not fix automatically. Open an architecture discussion.**

The deciding question is whether a provider-sourced trip is conceptually owned by a platform
operator. That is a domain question for `inventory-service` and `operator-service` owners, not an
implementation detail of `search-service`.

Note that option A is the only one that does not fabricate data, and it is what the current
`inventory-service` code already assumes.

### Interim state

For validation only, 31 rows were inserted **directly into the local search-service dev database**
with a synthesized `operator_id`. That data is local, uncommitted, and not reproducible from the
repository. It is **not** a fix — the Kafka listener still fails on every live catalog event. Any
fresh environment will have an empty search index until this item is resolved.

---

## 2. JWT key strategy across services

**Status:** blocking for any multi-service environment.

`auth-service`, `booking-service` and `payment-service` all set `ephemeral-keys: true` in their
`application-local.yml`. Each generates its **own** throwaway RSA keypair at startup, so a token
minted by `auth-service` fails signature verification at `booking-service` and `payment-service`,
and every authenticated call returns 401.

`booking-service`'s own README already flags this under "Remaining Integration Points". The
configuration mechanism for the real thing exists and works — `roadscanner.security.jwt.public-key-pem`
on verifiers, `private-key-pem` on the issuer — it is simply not wired for local runs.

### Interim state

End-to-end validation passed one shared keypair to all three services via environment variables
from a launcher script held outside the repository. **No key material and no launcher were
committed.** The checked-in `application-local.yml` files are unchanged and still request ephemeral
keys, so a fresh clone reproduces the 401s.

### Options

| Option | Notes |
|---|---|
| **A. Committed dev-only keypair** | A clearly-labelled non-secret keypair in the repo for the `local` profile. Zero-setup local runs; a key in version control, even a worthless one, sets a poor precedent |
| **B. Generated on first run** | A `make dev-keys` / script writing to a gitignored path each service reads. No key in the repo; adds a setup step |
| **C. Local secrets provider** | Mirrors dev/prod exactly (both already read from a secrets manager). Heaviest local setup |

### Recommendation

**Decide the permanent configuration before production.** Options A and B are both defensible for
local development; the important part is that the deployed profiles already read from a secrets
manager and must never inherit an ephemeral or committed key.

Whichever is chosen, `ephemeral-keys: true` should be removed from any service that verifies tokens
it did not issue — as written it is not merely insecure, it is non-functional across services.

---

## 3. `api-gateway` exposes no routes

**Status:** non-blocking; shapes client configuration today.

`api-gateway` is described in `docs/architecture/api-inventory.md` as the single client-facing
entry point enforcing JWT validation and rate limiting, but the service currently exposes no
routes. `customer-web` therefore addresses each service directly on its own port and pins its dev
server to `5173` because that is the origin each service's `application-local.yml` allow-lists for
CORS.

The frontend reads all service base URLs from environment variables
(`frontend/apps/customer-web/src/lib/api/config.ts`), so pointing them at a gateway is a
configuration change with no code change. Two things should be settled when the gateway lands:

- `/internal/**` must never be routed publicly — several services document that their internal
  endpoints have no per-request authentication in Phase 1 and rely on the network boundary.
- `/webhooks/**` must reach `payment-service` **without** JWT enforcement; it is authenticated by
  gateway signature instead.

### Recommendation

No action now. Revisit CORS allow-lists and the frontend's per-service URLs together when the
gateway is implemented.

---

## 4. Seat layouts exist for provider-synced trips despite a null `bus_id`

**Status:** informational — worth confirming it is intended.

Provider-synced trips are created with `busId = null`, yet `seat_layouts` is populated (30 rows
observed) and `GET /api/v1/inventory/trips/{tripId}/seat-layout` returns `200`. The seat-selection
path works, so this is not a defect today.

### Recommendation

Confirm the intended relationship between a trip's bus and its seat layout for provider-sourced
supply, so the coupling is documented rather than incidental. Related to item 1 — both stem from
provider-sourced trips carrying less first-party metadata than first-party trips.
