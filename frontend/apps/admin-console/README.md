# admin-console

The operator-facing console for the RoadScanner **provider registry**. It administers
`provider-integration-service` — the platform's single answer to "which providers exist, are they
in service, and can they authenticate" — and nothing else.

It is deliberately narrow. The console talks to exactly two services: `auth-service` to sign in,
and `provider-integration-service` for the registry. An admin tool that can reach every service is
a much larger blast radius than one that cannot.

## Screens

| Screen | Backing API | Notes |
|---|---|---|
| Dashboard | `GET /api/v1/providers` | One request; every card is a projection of it |
| Providers | `GET/POST/PUT /api/v1/providers`, `POST /{id}/{enable,disable,test}` | List, register, edit, put in/out of service |
| Credentials | `GET/PUT /api/v1/providers/{id}/credentials`, `POST /{id}/refresh-session` | Presence and freshness only — never values |
| Health | `GET /internal/api/v1/providers/{code}/health` | On-demand probes; nothing polls |
| Audit | — | **Placeholder.** No read endpoint exists; see below |

## Running locally

```bash
# from the repo root: start the infrastructure
docker compose up -d

# then run auth-service and provider-integration-service with the `local` profile
# (see each service's README)

# from this directory
npm install
npm run dev
```

The app starts on **`http://localhost:5174`**. That port is not arbitrary: every service's
`application-local.yml` allow-lists `http://localhost:5173` (customer-web) and
`http://localhost:5174` as CORS origins. Running on Next's default 3000 fails every request at the
preflight.

Sign in with an account holding the `ADMIN` role. Roles are granted through
`POST /api/v1/auth/roles`, which an existing administrator calls — this console deliberately
exposes no registration, since a tool that could mint its own administrators would be a
privilege-escalation path.

### Environment

| Variable | Default |
|---|---|
| `NEXT_PUBLIC_AUTH_API_URL` | `http://localhost:8081` |
| `NEXT_PUBLIC_PROVIDER_INTEGRATION_API_URL` | `http://localhost:8083` |

Both are read in `src/lib/api/config.ts` and nowhere else. When `api-gateway` lands, point them at
it — no other change is needed.

## Scripts

```bash
npm run dev        # dev server on :5174
npm run build      # production build
npm run typecheck  # tsc --noEmit
npm run lint       # eslint
npm test           # vitest run
```

## Architecture

Feature-based, with the API layer kept out of the components:

```
src/
  app/                     routes only — every page delegates to a feature view
    (console)/             the authenticated group: guard + shell
    login/                 outside the group, so it renders neither
  components/
    ui/                    Radix + cva primitives (the shadcn idiom)
    layout/                shell, sidebar, header, admin guard
  features/
    providers/             api.ts · hooks.ts · schema.ts · components/
    credentials/           api.ts · hooks.ts · schema.ts · components/
    health/                api.ts · hooks.ts · components/
    dashboard/             summary.ts (pure) · components/
    audit/                 api.ts (contract only) · components/
  lib/
    api/                   client, config, wire types, query keys
    store/                 zustand auth session
    utils/                 cn, formatting
```

**One wire model.** `src/lib/api/types.ts` mirrors the Java records one-for-one — same field
names, same nullability. If a field is not returned by a controller today, it does not appear
there. Features import those types; none declares its own.

**`app/` holds routes, not logic.** Every page is a few lines delegating to a feature view, so a
screen can be tested and moved without touching routing.

### Relationship to `customer-web`

The design tokens (`app/globals.css`) and the `components/ui` primitives are copied from
`customer-web` character-for-character. That duplication is deliberate and temporary:
`frontend/packages/config` and `frontend/packages/ui-components` exist as empty placeholders, so
there is no shared package to import from, and introducing a workspace was outside this sprint's
scope. **The two apps must not drift** — when those packages are populated, both files move there
wholesale.

The auth layer (`lib/api/client.ts`, `lib/store/auth-store.ts`) follows the same shape for the same
reason, with one difference: the persisted session key is `roadscanner.admin.auth`, not
`roadscanner.auth`, so a traveller session can never be mistaken for an admin one if both apps ever
sit behind a single gateway origin.

## Decisions worth knowing

**Credentials are write-only, and this app treats them that way.** Secret fields are
`type="password"` with no reveal toggle, autocomplete is off so no password manager captures a
partner secret, and the form clears itself the moment a write succeeds — leaving a typed secret in
component state keeps it readable from the DOM and any devtools snapshot for as long as the screen
is open. Nothing is cached; the only thing stored is the returned summary (presence flags and a
timestamp). `ProviderCredentialsResponse` has no field that could carry a value, so there is
nothing to render even by accident. These guarantees are asserted in
`features/credentials/components/credential-form.test.tsx`.

**Health is probed, never polled.** `GET /internal/api/v1/providers/{code}/health` runs
`CheckProviderHealth`, which calls the provider, records the outcome and returns it. A screen that
fetched on mount would turn opening a browser tab into live traffic against every partner the
platform integrates with; one that polled would do it continuously. So every probe is an explicit
action, and results live in the query cache for the session. The durable record the scheduled
`ProviderHealthMonitor` maintains has **no read-only endpoint** — when it gets one, the Health
screen should read that on load and keep "Probe" for the explicit check.

**Optimistic updates are used in exactly one place**: the in-service toggle. Both routes are
idempotent, the only field that changes is the boolean already on screen, and a failure rolls the
row back with a toast. Everywhere else the server's response is written into the cache after it
arrives — reporting a secret as stored before the server confirms it is precisely the lie an admin
would act on.

**Enabling is one click; disabling asks first.** Putting a provider into service is additive and
reversible by the same switch. Taking it out withdraws live supply from search and booking, so it
is confirmed. Confirming both would train people to click through the dialog.

**Registering never enables.** The backend keeps enabling out of create and update on purpose, so
the form has no "enabled" control rather than implying a capability the API does not offer.

## Known gaps

- **Audit has no backend endpoint.** `provider-integration-service` writes an `audit_records` row
  for every provider operation and publishes each to the `provider-integration-events` Kafka topic,
  but exposes no REST route to read any of it. The screen says so plainly and shows the shape it
  will take; the client contract is already written against the migration's columns in
  `features/audit/api.ts`. Wiring it up is a data-source change, not new work.
- **No end-to-end tests.** The suite here is unit-level: pure aggregation, schemas, and the
  credential form's security guarantees. Flows that need a live registry belong in an e2e suite
  this app does not have yet.
- **Provider health history is not visible** beyond the current state and last success/failure
  timestamps, because that is all `ProviderHealthResponse` carries.
