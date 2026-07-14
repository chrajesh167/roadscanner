# Auth Service — Exception Strategy

## Hierarchy (Conceptual)

A small root exception type for the service, with specific, meaningfully-named subtypes for each distinct business failure — not generic runtime exceptions caught and re-thrown, and not one giant catch-all. Conceptual subtypes:

- `InvalidCredentialsException` — login failed (covers both "unknown identifier" and "wrong password" — see below)
- `AccountLockedException` — too many recent failed attempts
- `TokenExpiredException` — access or refresh token past expiry
- `TokenReusedException` — a superseded refresh token was presented again (see `security-design.md`'s reuse-detection)
- `ResetTokenInvalidException` — password-reset token unknown, expired, or already used
- `PasswordPolicyViolationException` — new password fails complexity policy
- `IdentifierAlreadyRegisteredException` — registration attempted with a taken identifier

Domain and application layers throw these directly; `adapter/in/rest` is the only layer that translates them into a transport-facing representation — consistent with `package-structure.md`'s dependency direction, and with `.claude/CODING_STANDARDS.md`'s "global exception handling" rule (one mapping layer, not scattered try/catch throughout the codebase).

## Security-Sensitive Handling

Two rules that override the "distinct, meaningfully-named exception" pattern above:

1. **`InvalidCredentialsException` is intentionally the same exception whether the identifier doesn't exist or the password is wrong.** The exception hierarchy could distinguish these internally for logging purposes, but the client-facing translation must not — see the enumeration-protection rule in `security-design.md` and `api-contract.md`. This is the one place where an otherwise-good practice (specific exceptions) is deliberately not exposed all the way to the client.
2. **No exception ever surfaces internal detail to a client** — no stack traces, no database error text, no indication of which internal component failed. Every client-facing error is a stable, generic message; the specific exception type and its full detail go to the log only.

## Retryable vs. Non-Retryable

- **Transient** (a database timeout, a Redis connection blip) — safe for the client to retry, and distinguishable in the response so a well-behaved client knows to.
- **Permanent** (wrong password, expired token, policy violation) — not safe to blindly retry, and in the case of failed login attempts, must count toward account lockout (`security-design.md`) rather than being silently retryable.

## Correlation With Logging

Every thrown exception is logged with the request's correlation ID (`docs/architecture/high-level-design.md` §9, expanded in `logging-observability.md`), with sensitive fields redacted per that document's redaction policy — a security-relevant failure must be traceable end-to-end without ever putting a credential or token in a log line.
