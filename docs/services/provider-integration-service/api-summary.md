# API Summary

Internal-only surface, under `/internal/api/v1/providers/{providerType}` — see this service's own
`README.md` "API Surface" for the endpoint-level contract and `/swagger-ui.html` for the live
OpenAPI spec once running. No authentication or authorization is implemented in this service
itself; see `boundaries.md` and `README.md`'s "Remaining Integration Points" for where that
responsibility is expected to sit (`api-gateway`, not yet built).

## Operation Categories

- **Session lifecycle** — authenticate, refresh
- **Trip discovery** — search, seat map
- **Reservation lifecycle** — block, release
- **Booking** — confirm, download ticket
- **Provider metadata** — capability discovery, health

Every session-scoped operation (everything except capability discovery and health) requires a
`sessionId` obtained from a prior authenticate call and takes it as a path segment
(`/sessions/{sessionId}/...`) — see `use-cases.md` and `ActiveSessionResolver`'s role in enforcing
that the session is still valid on every such call.
