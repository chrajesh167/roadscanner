# Sprint 2.1 — Provider production hardening

**Status:** implemented 2026-07-30 · no new business features · no REST contract changes

Five pieces of shared infrastructure that every provider integration will depend on. They land
before the first real provider integration deliberately: each one is far cheaper to build once,
now, than to retrofit across three adapters later — and two of them (credential encryption,
non-idempotent call handling) are the kind of thing that is expensive to get wrong in production.

## Why these are shared, not per-provider

The platform will run FlixBus, RedBus, AbhiBus, rail and airline providers. Every one of them
needs a timeout, a retry rule, a backoff curve, an error vocabulary, metrics and stored
credentials. If each adapter supplies its own:

- The behaviours drift. FlixBus retries three times, the next provider twice, a third not at all —
  and nobody decided that, it just happened.
- The metrics drift with them. A dashboard that exists for one provider and not another
  under-reports silently, which is worse than having no dashboard.
- A safety rule has to be re-remembered per adapter. "Never retry a booking confirmation" is one
  reviewable decision here; spread across five adapters it is five chances to get it wrong, and
  the failure mode is a double-booked traveller.

So the execution policy, the exception hierarchy, the metrics and the credential encryption all
live in provider-neutral code, and an adapter gets them by existing rather than by opting in.

## 1. Credential encryption at rest

`partner_password` and `partner_token` are encrypted with AES-256-GCM. The aggregate and every use
case still deal in plaintext — encryption happens at the persistence boundary via a JPA
`AttributeConverter` on those two columns.

**Why the converter and not the repository.** Encrypting in the adapter's `save` would be one new
write path away from a plaintext leak. Encryption on the *column* is not something a caller
performs, so no future code path can forget it.

**Why GCM.** It is authenticated. A tampered or wrong-key ciphertext fails to decrypt rather than
yielding plausible garbage that then gets sent to a provider as a password — which would surface as
a confusing authentication failure pointing at entirely the wrong thing. A fresh 12-byte IV per
encryption is prefixed to the output; reusing an IV under GCM leaks the key stream.

**Why it is not a destructive migration.** Stored values are scheme-tagged (`enc:v1:…`). Decryption
returns anything without a marker unchanged, so rows written before this sprint keep working and
are re-stored encrypted on their next write. No backfill, no downtime.

**The KMS seam.** `CredentialCipher` is a port. Swapping the local cipher for a KMS-backed one is a
bean definition in `CredentialEncryptionConfig` — the entity, converter, aggregate, use cases and
tests all depend on the interface. Because ciphertext is scheme-tagged, that migration can be
incremental rather than big-bang.

`partner_email` is deliberately left readable: it identifies a partner account rather than granting
access to one, and keeping it queryable means "which account is this row for" is answerable during
an incident without a decryption key. It is still never returned by any endpoint.

## 2. Provider execution policy

`execution/` holds `ProviderExecutionPolicy`, `ProviderExecutionExecutor`, `RetryStrategy` and
`BackoffStrategy`. Every `ProviderClient` is wrapped by `ExecutionPolicyProviderClient` when the
registry is built, so the policy applies to all providers identically.

**Settings come from the provider row.** `timeout_ms` and `retry_count` are read per call, so an
operator can lengthen one slow provider's timeout through the admin API without lengthening it for
everything sharing the code path. That is what those columns were added for in Sprint 2.

**Idempotency is encoded centrally.** Seat block, seat release and booking confirmation run
single-attempt: a timeout on those is inconclusive, so a retry risks a second hold or a duplicate
booking. Reads retry. Health probes run single-attempt too — a probe that quietly retries reports a
provider as healthy when it needed three goes, which is the exact state the probe exists to reveal.

**Backoff is exponential with full jitter and a cap.** Without jitter, every in-flight request fails
at the same moment and retries in synchronised waves, often re-breaking a recovering provider. The
cap stops a high retry count holding a request thread for minutes.

**Threading.** The timeout is enforced by running the call on a bounded worker pool and bounding the
wait, because the per-provider timeout is dynamic while an HTTP client's socket timeout is fixed
when the bean is built — and a socket timeout cannot bound a call that connects fine then streams
slowly forever. The cost: a timed-out call keeps its worker until its own socket timeout fires. The
pool is therefore bounded and rejection is surfaced as an unavailable provider rather than queued,
so saturation is visible instead of appearing as slowly-growing latency.

MDC is captured from the calling thread and reinstated inside the worker, then restored — otherwise
every provider log line would lose its correlation id, and a pooled thread would leak one request's
id into the next.

**Resilience4j `@Retry` was removed** from the FlixBus clients and from `application.yml`. Leaving
both would compound: 3 resilience4j attempts × 3 policy attempts = 9 calls to a struggling provider.
Circuit breaker, rate limiter and bulkhead remain — they are per-provider protections that do not
overlap with per-call execution.

## 3. Exception hierarchy

Added `ProviderTimeoutException`, `RateLimitedException`, `ProviderValidationException` and
`ProviderResponseException` under the existing `ProviderIntegrationException` root.

The distinctions are not cosmetic — `RetryStrategy` reads `ProviderError.retryable`, so the type a
failure is translated into decides whether it is repeated:

| Failure | Retryable | Status | Why the distinction earns its place |
|---|---|---|---|
| `ProviderTimeoutException` | yes | 504 | "went quiet" vs "refused" are different operational problems |
| `RateLimitedException` | yes | 429 | carries the provider's `Retry-After`; ignoring it gets us throttled harder |
| `ProviderValidationException` | **no** | 422 | the request is wrong; retrying burns quota for the same answer |
| `ProviderResponseException` | **no** | 502 | a contract change, visible as itself rather than buried in flakiness |

`ProviderExceptionContainmentTest` enforces structurally that no FlixBus, Spring HTTP-client or
Jackson type appears in domain code, and that every FlixBus class stays package-private — so a
future adapter cannot quietly widen a signature and couple every caller to one provider's error
vocabulary.

The root is still named `ProviderIntegrationException` rather than the brief's `ProviderException`.
Renaming it would touch every existing exception, handler and test for no behavioural gain.

## 4. Metrics

`ProviderMetrics` records `provider.call.duration` (a timer over the whole call, retries included —
what the caller actually waited) plus counters for search/authentication success and failure,
timeouts and retries. Tagged `provider`, `operation`, `status`, all from closed sets so one bad
request cannot explode Prometheus cardinality.

Retries are counted per retry rather than per call: the ratio against call count shows how much
hidden work a provider is costing, which moves well before its failure rate does.

## 5. Google Places rate limiting

A token bucket in front of the metered autocomplete API: `rate-limit-requests-per-minute` sustained,
`rate-limit-burst-size` accumulated. Exceeding it returns **429** — distinct from the 503 an
unavailable provider gets, because one says "slow down" and the other says "try later", and a client
that cannot tell them apart does not know whether backing off helps.

The check sits **after** the cache lookup. A cache hit costs the provider nothing, so spending a
permit on it would throttle traffic that was never going to reach Google. A refusal is never cached
— that would extend a momentary limit into a full TTL of empty dropdowns.

**Per instance, not per cluster.** With N replicas the effective ceiling is N × the configured rate,
so the value is a per-instance budget. A Redis round trip on every keystroke would add latency to
the exact path this protects, and the shared cache already absorbs most repeat traffic. If a hard
global cap becomes necessary, a Redis-backed implementation of `PlaceAutocompleteRateLimiter`
replaces the bucket without touching the use case.

## Configuration

Nothing above is hardcoded.

| Setting | Where | Scope |
|---|---|---|
| `timeout_ms`, `retry_count` | `provider_configurations` row | per provider, changed via admin API |
| `backoff-initial/-multiplier/-max/-jitter`, `pool-size` | `roadscanner.provider.execution` | platform-wide |
| `credential-encryption.key` / `.ephemeral-key` | `roadscanner.security` | per environment |
| `rate-limit-requests-per-minute`, `rate-limit-burst-size` | `roadscanner.google-places` | per environment |

The split is deliberate: what describes a *provider* lives on its row; what describes *this
service's* behaviour under load lives in configuration. Neither has a shipped default that would be
wrong in production — the encryption key fails startup if absent unless ephemeral keys are
explicitly opted into.
