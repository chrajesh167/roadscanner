# customer-web

The traveller-facing RoadScanner app: search, seat selection, booking and payment.

## Running it

```bash
npm install
cp .env.example .env.local
npm run dev          # http://localhost:5173
```

**The port is deliberate.** `api-gateway` exposes no routes yet, so the browser calls each service
directly, and every service's `application-local.yml` allow-lists `http://localhost:5173` as a CORS
origin. Running on Next's default 3000 gets every request rejected by the backend.

Backends this app expects (start with `SPRING_PROFILES_ACTIVE=local`, plus the root
`docker-compose.yml` for Postgres/Kafka/Redis):

| Service | Port | Used for |
|---|---|---|
| auth-service | 8081 | register, login, refresh, logout, password reset |
| search-service | 8082 | trip search, trip detail, place suggestions |
| booking-service | 8085 | seat map, holds, bookings, cancel, ticket |
| payment-service | 8086 | initiate payment, poll payment status |

## Scripts

| Command | Does |
|---|---|
| `npm run dev` | Dev server on 5173 |
| `npm run build` | Production build |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint |

## Structure

```
src/
  app/                    routes (App Router)
    page.tsx              landing
    login/ register/      auth
    search/ search/results/
    trips/[tripId]/       trip detail
    trips/[tripId]/seats/ seat selection
    booking/passengers/   passenger details + delivery contact; places the seat hold
    booking/[bookingId]/payment/  payment
    booking/[bookingId]/success/  confirmation
    bookings/             history + [bookingId] detail
    profile/ settings/
  components/
    ui/                   design system (button, card, input, select, dialog,
                          badge, feedback, motion, misc)
    layout/               header, footer, page shell, auth guard
    search/               search form, place autocomplete, trip card, filters
    booking/              seat map, hold timer, flow steps
    auth/                 auth split layout
  lib/
    api/                  axios clients, endpoints, wire types, query keys
    hooks/                TanStack Query hooks
    store/                Zustand: auth, booking flow, preferences
    validation/           Zod schemas
    utils/                cn, formatters
```

Each route is a thin server component (metadata + `params`) delegating to a `*-view.tsx` client
component. That keeps `generateMetadata`/`params` on the server while the interactive work stays in
one clearly-marked client file.

## Design system

Tokens live in `src/app/globals.css` under `@theme` — Tailwind v4 generates the utilities from
them, so `--color-surface` gives you `bg-surface` and `--text-h1` gives you `text-h1`. Components
never hardcode a hex value.

Dark, near-black substrate, three elevation steps, one violet accent, semantic status colours.
Glassmorphism is used sparingly: sticky header, dialogs, popovers, hero search. Motion is three
gestures (`Reveal`, `Stagger`, `Pressable`), all of which collapse under
`prefers-reduced-motion`.

## Notes on backend behaviour

Two things the UI surfaces honestly rather than papering over:

**Payment settles out-of-band.** `POST /api/v1/payments` returns a `PENDING` payment; it only
reaches `CAPTURED` when the gateway calls `POST /webhooks/{gatewayType}`. The payment screen polls
`GET /api/v1/payments/{id}/status` and stops on any terminal status. Locally the gateway adapters
are deterministic stubs that never call back, so the status legitimately stays `PENDING` — the UI
says "waiting for your bank" and the booking shows as reserved-not-confirmed. That is the real
system behaviour, not a bug in this app.

**Profile and Settings have no backend.** `user-service` owns Profile Management in the
architecture but exposes no endpoints. So Profile renders what the session and booking history
actually know, and Settings splits into local preferences (localStorage) and the auth operations
that *are* real: password-reset request, logout, logout-all. Nothing is faked against an invented
endpoint.

**Availability can be unknown.** `TripResponse.availabilityKnown` being false means the live
overlay could not be resolved — which is not the same as "sold out". Results say "availability at
checkout" rather than implying a seat count.

## Idempotency

An idempotency key is minted once per booking (in `booking-flow-store`) and reused for every
payment attempt on that booking, which is what makes a double-submit return the existing payment
instead of charging twice. The key is required by `POST /api/v1/payments`.
