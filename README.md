# RoadScanner

RoadScanner is an AI-powered, cloud-native travel platform. The initial product is intercity **bus booking**; the platform is architected from day one to expand into trains, flights, hotels, and cabs without a rewrite.

This repository is a **monorepo** containing every backend service, frontend application, and the infrastructure-as-code needed to build, deploy, and operate RoadScanner.

> **Status:** Repository scaffolding only. No business logic, APIs, or entities exist yet — this commit establishes the structure the engineering org will build into.

---

## Why a monorepo

- **Atomic cross-service changes** — a contract change touching `booking-service` and `payment-service` ships in one PR, reviewed together, versioned together.
- **Shared libraries without a package registry hop** — `backend/shared-libs` and `frontend/packages` are consumed directly; no publish-then-bump cycle while the platform is small.
- **One source of truth for infra** — Terraform, Kubernetes manifests, and CI live next to the code they deploy, so infra drift is a diff, not a mystery.
- **Consistent tooling** — one place to enforce linting, formatting, commit conventions, and CI gates across every service.

As the org grows, individual services can be extracted to their own repositories without changing their internal structure — each service is already self-contained.

---

## Architectural principles

- **Microservices** — each business capability (auth, booking, payment, ...) is an independently deployable service with its own datastore boundary.
- **Domain-Driven Design (DDD)** — service boundaries follow business subdomains (bounded contexts), not technical layers.
- **Clean / Hexagonal Architecture** — each service will separate domain logic from delivery mechanisms (REST, Kafka) and infrastructure (database, cache) behind ports and adapters, so the domain has zero framework dependencies.
- **SOLID** — enforced at code review time; shared libraries exist precisely so services depend on abstractions, not on each other's implementation details.

These principles govern how each service's *internal* package structure will be scaffolded next — that is intentionally out of scope for this commit.

---

## Repository structure

```
roadscanner/
├── .github/workflows/     CI/CD pipeline definitions (GitHub Actions)
├── docs/                  Architecture decisions, diagrams, API specs, runbooks, standards
├── docker/                Local dev container configs (Postgres, Redis, Kafka, Nginx, observability stack)
├── infrastructure/        Terraform (AWS) and Kubernetes manifests, environment-scoped
├── backend/               Java 21 / Spring Boot microservices + shared libraries
└── frontend/              React / TypeScript applications + shared packages
```

### `.github/`

CI/CD and repository automation, kept at the root because GitHub only discovers workflows there.

- **`workflows/`** — GitHub Actions pipelines (build, test, lint, container image publish, deploy triggers). Split per service/app as the platform grows, rather than one monolithic pipeline, so a change to `booking-service` doesn't gate a deploy of `customer-web`.

### `docs/`

Everything a new engineer or an auditor needs that isn't derivable from the code itself.

- **`architecture/decisions/`** — Architecture Decision Records (ADRs). One immutable file per significant technical decision (e.g., "why Kafka over SQS", "why per-service database"), so *why* survives past the people who decided it.
- **`architecture/diagrams/`** — System context, container, and sequence diagrams (C4 model). Source files (e.g., draw.io, Mermaid) and exports.
- **`architecture/services/`** — One page per microservice: its bounded context, owned data, public contracts, and upstream/downstream dependencies. Together with `diagrams/`, this *is* the high-level design (HLD) — system and container level, no per-class detail.
- **`api/`** — API contracts and specifications (OpenAPI/AsyncAPI) shared across teams as the source of truth for service interfaces, independent of any single service's code.
- **`deployment/`** — Release process, environment promotion, and rollout/rollback strategy. Kept separate from `runbooks/` deliberately: this is "how a change ships," runbooks are "what to do when something is on fire" — different audiences, different lifecycle.
- **`runbooks/`** — Operational procedures: incident response, on-call escalation, rollback steps, known failure modes.
- **`onboarding/`** — New-engineer setup guides: local environment, access requests, first PR checklist.
- **`standards/`** — Engineering conventions: coding style, git branching/commit strategy, PR review bar, testing strategy, definition of done.

Deliberately **not** present at the top level: low-level design (LLD) and entity-relationship diagrams (ERD). Both go stale fast and are most useful reviewed alongside the code they describe, so they belong inside each service's own directory once that service is scaffolded — not centralized here where they'd drift from reality. This is also a polyglot-persistence architecture (each service owns its schema per DDD), so a single central ERD would misrepresent the design rather than document it.

### `docker/`

Local development infrastructure, composed via Docker Compose (added when services exist). Each subfolder holds that component's local config so `docker-compose.yml` files stay declarative.

- **`postgres/`** — Local Postgres init scripts / config; each service still owns its own schema and Flyway migrations inside its own service folder.
- **`redis/`** — Local Redis config for caching and session/rate-limit stores.
- **`kafka/`** — Local Kafka/broker config for the event backbone between services.
- **`nginx/`** — Local reverse-proxy config, standing in for the API Gateway during local development.
- **`observability/prometheus/`**, **`grafana/`**, **`loki/`** — Local metrics (Prometheus), dashboards (Grafana), and log aggregation (Loki) so the observability stack that runs in production also runs on a laptop.

### `infrastructure/`

Infrastructure as code. Deliberately separated from `docker/` (local dev) — this is what provisions and runs real AWS environments.

- **`terraform/modules/`** — Reusable Terraform modules (VPC, RDS, ElastiCache, MSK, EKS, IAM) — the building blocks.
- **`terraform/environments/{dev,staging,prod}/`** — Environment-specific compositions of those modules with their own state and variables, so a change to `dev` can never accidentally touch `prod`.
- **`kubernetes/base/`** — Base Kustomize/Helm manifests shared by every environment.
- **`kubernetes/overlays/{dev,staging,prod}/`** — Environment-specific patches (replica counts, resource limits, secrets refs) layered on top of `base/`. (Kubernetes is a future milestone per the tech stack; the structure is ready ahead of time.)
- **`scripts/`** — Operational and provisioning scripts that don't belong to Terraform or a single service (bootstrap, seed, disaster-recovery drills).

### `backend/`

Java 21 / Spring Boot 3, one Maven module per deployable unit.

- **`services/`** — One directory per microservice, each an independently buildable and deployable Spring Boot application:
  - `api-gateway` — single entry point; routing, auth token validation, rate limiting.
  - `auth-service` — authentication, JWT issuance/rotation, identity.
  - `user-service` — customer/traveler profile and account data.
  - `operator-service` — bus operator accounts, fleets, routes they own.
  - `inventory-service` — seat/trip inventory and availability.
  - `search-service` — trip search and ranking.
  - `booking-service` — reservation lifecycle and booking state machine.
  - `payment-service` — payment processing, refunds, ledger integration.
  - `notification-service` — email/SMS/push delivery.
  - `analytics-service` — event ingestion and reporting/BI feeds.
  - `review-service` — ratings and reviews.

  Each service folder is empty today; it will later contain its own `pom.xml`, hexagonal-architecture package layout (`domain` / `application` / `adapter-in` / `adapter-out`), and Flyway migrations — scoped to that service alone, matching its DDD bounded context.

- **`shared-libs/`** — Cross-cutting Maven libraries consumed by services as dependencies, never the other way around (services never depend on each other's code — only on events/APIs).
  - `common-core` — base exceptions, DTOs, and utilities with no framework dependency.
  - `common-security` — shared Spring Security / JWT validation configuration.
  - `common-messaging` — Kafka producer/consumer abstractions and event envelope contracts.
  - `common-observability` — shared logging, tracing, and metrics wiring for the Prometheus/Grafana/Loki stack.
  - `common-persistence` — shared JPA base entities/auditing and Flyway conventions.
  - `common-testing` — shared test fixtures, Testcontainers setup, and contract-test utilities.
  - `platform-bom` — a Maven Bill of Materials for aligning dependency versions (Spring Boot, Kafka client, Testcontainers, ...) across all 11 services so they don't drift independently. Named `platform-bom`, not `common-*`, because it is a POM-only version-alignment artifact, not a code library services import.

### `frontend/`

React + TypeScript, structured as a workspace of independently deployable apps and shared packages.

- **`apps/`** — Deployable frontend applications, each its own Vite project:
  - `customer-web` — the public-facing booking site travelers use.
  - `operator-portal` — the console bus operators use to manage fleets, routes, and inventory.
  - `admin-console` — internal tooling for the RoadScanner team (support, ops, content).
- **`packages/`** — Shared code consumed by the apps above, never the reverse.
  - `ui-components` — the shared design system (Tailwind-based) component library.
  - `api-client` — typed API clients and React Query hooks generated from/aligned to the `docs/api` contracts.
  - `config` — shared ESLint/TypeScript/Tailwind configuration so every app enforces the same standards.
  - `utils` — framework-agnostic shared utilities (formatting, validation helpers).

---

## Tech stack

| Layer | Choices |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, JWT, PostgreSQL, Redis, Kafka, Flyway, Maven, Docker |
| Frontend | React, TypeScript, Vite, Tailwind CSS, React Query |
| Infrastructure | Docker, Docker Compose, AWS, Terraform, Kubernetes (future), GitHub Actions, Prometheus, Grafana, Loki |

## Status / next steps

This commit only establishes structure. Deliberately not yet done:

- No `pom.xml` / parent POM or Maven reactor wiring.
- No Spring Boot application code, controllers, or entities.
- No React application code.
- No database schemas or Flyway migrations.
- No Docker Compose file or Kubernetes manifests with real content.
- No CI pipeline logic inside `.github/workflows/`.

Each of the above is scoped as separate, deliberate follow-up work.

## License

MIT — see [LICENSE](./LICENSE).
