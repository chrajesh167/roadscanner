# Auth Service — Package Structure

Describes the intended package/module layout for implementation, following the platform's hexagonal architecture convention (`docs/architecture/high-level-design.md` §1). This is a structural blueprint, not code.

## Layout

```
auth-service/
├── domain/
│   ├── model/         Credential, RefreshToken, PasswordResetRequest, Role — as plain domain concepts,
│   │                   no framework annotations, no persistence awareness
│   ├── service/        Domain-level policies: PasswordHashingPolicy, TokenExpiryPolicy, PasswordComplexityPolicy
│   └── port/
│       ├── in/          Use-case interfaces: RegisterUser, AuthenticateUser, RefreshAccessToken,
│       │                 RevokeSession, RevokeAllSessions, RequestPasswordReset, ConfirmPasswordReset, AssignRole
│       └── out/         Outbound interfaces the domain depends on, owns none of the implementation:
│                         CredentialRepository, RefreshTokenRepository, PasswordResetRepository,
│                         PasswordHasher, TokenSigner, RevocationCache
├── application/
│   └── usecase/        One implementation per inbound port, orchestrating domain objects + outbound ports.
│                         No HTTP, no SQL, no Redis client — only ports.
├── adapter/
│   ├── in/
│   │   └── rest/        Controllers, request/response DTOs, DTO<->command mapping. The only layer
│   │                     aware that this is an HTTP service at all.
│   └── out/
│       ├── persistence/  JPA/Postgres implementations of the repository ports
│       ├── security/     JWT signing/verification implementation of TokenSigner, using shared
│       │                 primitives from backend/shared-libs/common-security
│       └── cache/        Redis implementation of RevocationCache
└── config/              Spring Boot wiring: bean configuration, security filter chain, OpenAPI config
```

## Dependency Direction

`domain/` depends on nothing outside itself. `application/` depends only on `domain/`. `adapter/*` depends inward on `application/` and `domain/`, never the reverse — no adapter is ever depended upon by the domain or application layers. This is what makes the domain testable without Spring, a database, or Redis running (see `testing-strategy.md`).

## Shared Library Usage

- `backend/shared-libs/common-security` — shared **JWT validation** primitives (used by every service that needs to verify a token) and shared cryptographic utility conventions (e.g., key-loading patterns). `auth-service` depends on it like any other service for verification.
- `backend/shared-libs/common-persistence` — shared JPA base entities/auditing conventions.
- `backend/shared-libs/common-observability` — shared logging/tracing/metrics wiring.
- `backend/shared-libs/common-testing` — shared Testcontainers setup and fixtures.
- `backend/shared-libs/common-core` — base exceptions/DTO conventions, utilities with no framework dependency.
- `backend/shared-libs/platform-bom` — dependency version alignment.

**What is deliberately *not* in a shared library:** token **issuance** logic (which claims to include, access-token lifetime, refresh rotation policy) is `auth-service`'s own business logic, living in its `domain`/`application` layers — not `common-security`. Per `.claude/ARCHITECTURE_RULES.md`, "business logic never belongs inside shared libraries," and issuance policy is exactly that: a decision only `auth-service` is entitled to make, since it's the platform's only token issuer. `common-security` gives every *other* service the ability to verify what `auth-service` issues — it does not give any service the ability to issue tokens itself.

## DTO Responsibilities

DTOs exist only at `adapter/in/rest` and translate HTTP request/response shapes to and from the `application` layer's command/result objects (e.g., a `RegisterRequest` DTO maps to a `RegisterUserCommand`). The domain layer never sees a DTO — it works exclusively with its own model and port interfaces. This split exists so a future non-REST adapter (an internal admin CLI, or a gRPC interface for service-to-service calls) could reuse the same use-cases without any REST-specific type in the way.

## Organizing Within a Layer

Hexagonal architecture is inherently package-by-layer at the top level shown above. Within a layer (e.g., `adapter/in/rest`, `application/usecase`), organize by feature/use-case (registration, login, token, password-reset) rather than one flat package per layer — this keeps `.claude/CODING_STANDARDS.md`'s "no god classes" honest as the service grows past its initial handful of operations.
