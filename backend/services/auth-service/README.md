# Auth Service

Authentication and authorization only. Does not own user profile data — see
[`docs/services/auth-service/overview.md`](../../../docs/services/auth-service/overview.md) and
the rest of that directory for the full design.

**Status: feature-complete for Phase 1.** All layers are implemented: domain, application
use cases, persistence (Postgres/Flyway), security (Spring Security + RS256 JWT), Redis
revocation cache, and the REST surface with OpenAPI documentation. See "API Surface" and
"Remaining Integration Points" below.

## API Surface

All endpoints under `/api/v1/auth` (see `/swagger-ui.html` for the full contract):

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /register` | none | Create identity (default role `TRAVELER`), starts a session |
| `POST /login` | none | Verify credentials, issue access + refresh tokens |
| `POST /refresh` | refresh token in body | Rotate the refresh token, new access token; reuse of a rotated token revokes every session for the user |
| `POST /logout` | refresh token in body | Revoke one session (idempotent) |
| `POST /logout-all` | Bearer JWT | Revoke every session for the caller |
| `POST /password-reset/request` | none | Always `202`, identical for unknown identifiers (enumeration protection) |
| `POST /password-reset/confirm` | reset token in body | Single-use; changes password, revokes all sessions |
| `POST /roles` | Bearer JWT, `ADMIN` | Internal role elevation (append-only role history) |

## Requirements

- Java 21
- Docker (for local Postgres/Redis via `docker-compose.yml` at the repo root, and for
  Testcontainers-backed integration tests)

No local Maven install required — use the wrapper (`./mvnw`).

## Running Locally

```bash
# from the repo root: start Postgres + Redis
docker compose up -d

# from this directory: run the app natively against them
./mvnw spring-boot:run
```

The app starts on `:8081` with the `local` profile assumed by `application-local.yml`
(`localhost` Postgres/Redis — matches the ports docker-compose exposes). Activate it explicitly
if not relying on your IDE's default: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.

Useful local endpoints once running:
- `http://localhost:8081/actuator/health`
- `http://localhost:8081/swagger-ui.html` (empty until controllers exist)

### Running Fully Containerized

```bash
docker build -t roadscanner/auth-service .
docker run --network host -e SPRING_PROFILES_ACTIVE=local -p 8081:8081 roadscanner/auth-service
```

(`docker-compose.yml` intentionally does not include `auth-service` itself — see the comment at
the top of that file for why.)

## Building & Testing

```bash
./mvnw compile          # compiles cleanly — verified as part of this bootstrap
./mvnw test              # requires Docker running (Testcontainers — Postgres + Redis)
./mvnw package            # builds the executable jar
```

`./mvnw test` runs the full suite: framework-free unit tests for the domain and application
layers (these are the bulk, per `testing-strategy.md`), crypto adapter tests (BCrypt, RS256
sign/verify/tamper), Testcontainers-backed integration tests for the persistence adapters and
the Redis revocation cache, and `AuthServiceEndToEndTest` — full HTTP flows
(register → login → refresh → logout) plus every scenario in `testing-strategy.md`'s
"Security-Specific Scenarios" list (reuse detection, enumeration protection, lockout,
tampered/expired tokens, single-use reset, RBAC).

If Docker is provided by Colima rather than Docker Desktop, Testcontainers needs:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
```

## Configuration Profiles

| Profile | Use | Datasource / Redis |
|---|---|---|
| `local` | Running on a developer machine | `localhost`, matching `docker-compose.yml` |
| `dev` | Deployed dev environment | Every value from an env var, no hardcoded fallback for secrets |
| `test` | Test execution | Only non-infrastructure properties (e.g. CORS origins); datasource/Redis come from Testcontainers via `@ServiceConnection` |

No profile is active by default (`spring.profiles.active` is deliberately unset in
`application.yml`) — a missing profile should fail loudly, not silently boot against a guessed
environment.

## Architectural Decisions Made in This Bootstrap

**No `backend/shared-libs` dependency yet.** `docs/services/auth-service/package-structure.md`
specifies this service depends on `platform-bom`, `common-security`, `common-persistence`,
`common-observability`, and `common-testing`. Those modules are currently empty placeholder
directories with no `pom.xml` (see their `.gitkeep` files) — depending on non-existent Maven
artifacts would break the build, which conflicts with "the project must compile successfully."
This module therefore stands alone on `spring-boot-starter-parent` for now. When the shared
libraries are implemented, this `pom.xml` should be revisited to depend on them instead of (or
alongside) the equivalent starters used here. This is the one deliberate deviation from the
design docs in this bootstrap, made for build-correctness reasons.

**JWT signing keys are configuration, never source.** Per `security-design.md`, the RS256 pair
arrives as PEM via `roadscanner.security.jwt.private-key-pem` / `.public-key-pem` (sourced from
the secrets manager in deployed environments — see `application-dev.yml`). The `local` and
`test` profiles instead set `roadscanner.security.jwt.ephemeral-keys: true`, which generates a
throwaway pair at startup with a loud warning; a deployed profile with neither configured
fails startup deliberately. Generate a real pair with:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem
```

**Application layer is wired explicitly, not component-scanned.** Use-case classes carry no
Spring stereotypes (only `@Transactional` boundaries); `config/UseCaseConfig` constructs every
one, keeping the layer framework-light per `implementation-roadmap.md` step 3 and every
dependency visible in one place.

**Reuse detection revokes all of the user's sessions, not only the reused chain.** The
repository ports expose lookup by user (not chain-walking), and over-revoking is the safe
direction to err — a strict superset of `security-design.md`'s "entire token family".

**No Lombok.** `.claude/CODING_STANDARDS.md` calls for constructor injection and meaningful
naming; Java 21 records and plain constructors cover the DTO/config code in this bootstrap
without an annotation-processing dependency. Revisit if entity/DTO boilerplate later makes the
trade-off worth it.

**Hibernate `ddl-auto: validate`, never `update`.** Flyway is the sole source of schema truth in
every environment, including local — see `docs/architecture/database-ownership.md`.

## Remaining Integration Points

Implemented but awaiting other platform components (tracked, not forgotten):

- **Password-reset token delivery.** The reset request is fully persisted and confirmable, but
  the raw token is currently discarded after hashing — dispatching it by email/SMS is
  `notification-service`'s job (`docs/services/auth-service/responsibilities.md`), and that
  service does not exist yet. Wire the delivery trigger when it does. The raw token is never
  logged (redaction rule in `logging-observability.md`).
- **Public-key distribution to other services** (e.g. a JWKS endpoint or config-based
  distribution) — decided when `api-gateway` integration happens (roadmap step 11), together
  with gateway routing and rate limiting.
- **Finalized OpenAPI spec published to `docs/api/`** (roadmap step 10) — the live spec is
  served at `/v3/api-docs` today.
- **Custom Prometheus counters** from `logging-observability.md`'s metric table (login
  failure rate, token-reuse-detected, lockout count). Standard HTTP/JVM metrics are exposed
  now; reuse detection and lockouts are currently visible via tagged WARN/ERROR audit logs.
- **`backend/shared-libs` adoption** — unchanged from the bootstrap decision above.

## Package Structure

Hexagonal architecture — see
[`docs/services/auth-service/package-structure.md`](../../../docs/services/auth-service/package-structure.md)
for the full rationale. Every layer is implemented, organized by feature within each layer:

- `domain/` — models, policies, ports, exceptions (framework-free; the only layer with zero Spring imports)
- `application/usecase/{registration,login,token,passwordreset,role}` — inbound-port implementations plus the raw-token-resolving flow services
- `adapter/out/persistence` — JPA entities/mappers/adapters for the four repository ports
- `adapter/out/security` — BCrypt hasher, opaque-token generator, RS256 JWT signer
- `adapter/out/cache` — Redis revocation cache (expendable, Postgres-authoritative)
- `adapter/in/rest/{registration,login,token,passwordreset,role}` — controllers and DTOs, plus `exception/` (global mapping) and `filter/` (correlation id)
- `config/` — Jackson, CORS, OpenAPI, Redis, security filter chain, JWT key material, properties, use-case wiring
