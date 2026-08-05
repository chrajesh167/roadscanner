import type { ProviderCapability } from '@/lib/api/types';

/**
 * Provider audit history — <strong>not implemented, because the backend exposes no endpoint for
 * it.</strong>
 *
 * <p>The data exists. `provider-integration-service` writes an `audit_records` row for every
 * provider operation (V3__create_audit_records.sql, indexed on
 * `(provider_type, occurred_at DESC)` — which is exactly the read this screen would need) and
 * publishes each one to the `provider-integration-events` Kafka topic. What is missing is a REST
 * route: there is no `AuditController` in `adapter/in/rest`, so no HTTP client can reach any of it.
 *
 * <p>This module is therefore a declaration of the contract rather than a client for it. Nothing
 * here calls the network, and the Audit screen renders a placeholder that says so. Inventing an
 * endpoint and shipping a screen that 404s in every environment would look like an integration
 * while being strictly worse than an honest gap.
 *
 * <p><strong>To wire this up</strong>, once a read route exists (the natural shape being
 * `GET /api/v1/providers/{id}/audit?page=&size=`, admin-gated like the rest of that controller):
 * replace {@link auditApi.list} with the request, drop the `AUDIT_UNAVAILABLE` guard from the
 * screen, and add a `queryKeys.audit` entry. The type below is derived from the migration's
 * columns, so it should need no change.
 */
export interface ProviderAuditRecord {
  id: string;
  providerType: string;
  /** The operation performed — capability names plus lifecycle events such as authentication. */
  eventType: ProviderCapability | string;
  outcome: 'SUCCESS' | 'FAILURE';
  occurredAt: string;
  durationMs: number | null;
  correlationId: string | null;
  detail: string | null;
}

/** Set while no read endpoint exists. The Audit screen branches on it. */
export const AUDIT_UNAVAILABLE = true as const;

export const auditApi = {
  /**
   * Intentionally unimplemented. Throwing rather than returning `[]` keeps a future caller from
   * mistaking "no endpoint" for "no audit history" — an empty list is a claim about the data, and
   * this module is in no position to make one.
   */
  async list(): Promise<ProviderAuditRecord[]> {
    throw new Error(
      'provider-integration-service exposes no audit read endpoint — see features/audit/api.ts',
    );
  },
};
