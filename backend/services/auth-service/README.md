# Auth Service

Authentication and authorization only. Does not own user profile data — see
[`docs/services/auth-service/overview.md`](../../../docs/services/auth-service/overview.md) and
the rest of that directory for the full design.

**Status: foundation bootstrap only.** No registration, login, JWT, refresh tokens, password
reset, repositories, entities, or controllers exist yet — see "What's Deliberately Not Here"
below. This gets the project compiling, running, and observable, so the next session can build
business logic directly on top of a working foundation instead of also fighting the scaffold.

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

`./mvnw test` boots the full Spring context against real, ephemeral Postgres and Redis
containers (see `src/test/java/.../testsupport/TestcontainersConfiguration.java`) — this is the
one test that exists today (`AuthServiceApplicationTests`), and it's a meaningful one: it proves
the configuration, JPA, Flyway, Redis, actuator, and OpenAPI wiring genuinely works end-to-end,
not just that the code compiles.

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

**No Spring Security dependency yet.** Adding `spring-boot-starter-security` without a
configured filter chain triggers Spring Boot's default security (basic auth on everything,
including `/actuator/health`), which would work against today's "foundation only, no
authentication yet" scope. It's added alongside JWT implementation — see
`docs/services/auth-service/implementation-roadmap.md` step 5.

**No Lombok.** `.claude/CODING_STANDARDS.md` calls for constructor injection and meaningful
naming; Java 21 records and plain constructors cover the DTO/config code in this bootstrap
without an annotation-processing dependency. Revisit if entity/DTO boilerplate later makes the
trade-off worth it.

**Hibernate `ddl-auto: validate`, never `update`.** Flyway is the sole source of schema truth in
every environment, including local — see `docs/architecture/database-ownership.md`.

## What's Deliberately Not Here

Per today's scope: authentication, JWT issuance/validation, refresh token lifecycle,
registration, login, password reset, RBAC enforcement, repositories, JPA entities, REST
controllers, and Flyway migrations (there's nothing to migrate yet — see
`src/main/resources/db/migration/README.md`). These are built in the sequence described in
`docs/services/auth-service/implementation-roadmap.md`, on top of this foundation.

## Package Structure

Hexagonal architecture — see
[`docs/services/auth-service/package-structure.md`](../../../docs/services/auth-service/package-structure.md)
for the full rationale. Each layer's package currently holds only a `package-info.java`
explaining its intended contents until business logic is implemented, except:

- `config/` — Jackson, CORS, OpenAPI, and Redis client wiring (generic, no business logic)
- `domain/exception/AuthServiceException` — the root of the exception hierarchy; business-specific subtypes come with their use cases
- `adapter/in/rest/exception/` — global exception handling (`GlobalExceptionHandler`, `ErrorResponse`)
- `adapter/in/rest/filter/CorrelationIdFilter` — correlation-id propagation for logging/tracing
