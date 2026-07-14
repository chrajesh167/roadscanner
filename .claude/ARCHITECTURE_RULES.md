# Architecture Rules

Every service owns its own database.

No direct database sharing.

Communication:

- REST
- Kafka

Caching:

- Redis

Authentication:

- JWT

Future:

- Saga Pattern
- Outbox Pattern
- CQRS

Every service must expose:

- Health endpoint
- Metrics
- OpenAPI

Services must remain loosely coupled.

Shared libraries contain only cross-cutting concerns.

Business logic never belongs inside shared libraries.