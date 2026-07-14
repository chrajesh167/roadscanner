# Non-Functional Requirements

These govern how the platform must behave, independent of any single feature. Numeric targets are initial engineering targets to design against, not committed SLAs — revisit once real traffic exists.

## Performance

- **NFR-1** Search returns results within ~2s (p95) for a given route and date.
- **NFR-2** Seat hold and checkout operations complete within ~1s (p95) — this path is latency-sensitive because a slow hold increases the odds of losing the seat to a concurrent traveler.

## Scalability

- **NFR-3** Client-facing stateless services (`api-gateway`, `search-service`, `booking-service`, etc.) scale horizontally with load; no service holds request-scoped state in process memory.
- **NFR-4** Seat inventory must handle high-contention writes on popular routes correctly — correctness (no overselling) takes priority over raw write throughput.
- **NFR-5** The architecture must accommodate new travel verticals (Phase 2+) without re-architecting existing Phase 1 services. See `docs/architecture/high-level-design.md` §12.

## Availability & Reliability

- **NFR-6** Target 99.9% uptime for the customer-facing booking path (search, book, pay).
- **NFR-7** No double-booking and no lost payment. Where availability and correctness conflict on the booking/payment path, correctness wins — the system should refuse the request rather than risk an inconsistent state.
- **NFR-8** Non-critical services being degraded or unavailable (e.g., `review-service`, `analytics-service`) must never block the booking path.

## Data Consistency

- **NFR-9** Each service owns and is the single source of truth for its own data; no service reads another service's database directly.
- **NFR-10** Eventual consistency (via Kafka events) is acceptable across most service boundaries. The booking↔payment path is the one exception and must be strongly consistent — see the saga/outbox approach in `docs/architecture/high-level-design.md` §6.

## Security

- **NFR-11** All customer-facing and inter-service traffic is authenticated; JWTs are validated at `api-gateway` and re-checked for authorization at each service boundary.
- **NFR-12** RoadScanner never stores raw payment card data; payment processing is delegated to a PCI-DSS-compliant payment gateway.
- **NFR-13** Sensitive PII (contact details, payment references) is encrypted at rest and in transit.
- **NFR-14** Operator and admin actions are governed by role-based access control.

## Observability

- **NFR-15** Every service exposes a health endpoint, Prometheus metrics, and an OpenAPI spec from its first deployable commit — not retrofitted later.
- **NFR-16** A correlation/trace ID propagates from `api-gateway` through every downstream synchronous and asynchronous call, so any customer-facing request can be traced end-to-end.
- **NFR-17** Logs are structured and shipped to a central store (Loki); dashboards (Grafana) exist for each service's health and key business events.

## Maintainability

- **NFR-18** Each microservice is independently deployable; no platform-wide coordinated release is required to ship a single service's change.
- **NFR-19** Shared libraries (`backend/shared-libs`) contain only cross-cutting concerns — never business logic — so services remain independently evolvable.

## Portability & Cost

- **NFR-20** All infrastructure is defined as code (Terraform); no untracked manual changes to staging/prod environments.
- **NFR-21** The local development environment (Docker Compose) mirrors production service topology closely enough to catch integration issues before deployment.

## Localization (forward-looking, minimal for Phase 1)

- **NFR-22** Currency and locale are not hardcoded to a single market, even though Phase 1 launches in one country/currency — Phase 2+ verticals are typically multi-market, and retrofitting this later is expensive.
