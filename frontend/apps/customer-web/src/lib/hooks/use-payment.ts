'use client';

import { useMutation, useQuery } from '@tanstack/react-query';
import { paymentEndpoints } from '@/lib/api/endpoints/payment';
import { queryKeys } from '@/lib/api/query-keys';
import type { InitiatePaymentRequest, PaymentStatus } from '@/lib/api/types';

/** Statuses from which the payment can no longer change without a new action. */
const TERMINAL: ReadonlySet<string> = new Set<PaymentStatus>([
  'CAPTURED',
  'FAILED',
  'CANCELLED',
  'EXPIRED',
  'REFUNDED',
]);

export function isTerminalPaymentStatus(status: string | undefined): boolean {
  return status !== undefined && TERMINAL.has(status);
}

export function useInitiatePayment() {
  return useMutation({
    mutationFn: ({
      body,
      idempotencyKey,
    }: {
      body: InitiatePaymentRequest;
      idempotencyKey: string;
    }) => paymentEndpoints.initiate(body, idempotencyKey),
  });
}

/**
 * Polls `GET /api/v1/payments/{id}/status` until the payment reaches a terminal state.
 *
 * A payment only advances past PENDING when the gateway calls back on
 * `POST /webhooks/{gatewayType}` — the client cannot drive that transition. Locally the gateway
 * adapters are deterministic stubs that issue no callback, so the status legitimately sits at
 * PENDING; the payment screen surfaces that as "awaiting confirmation" rather than pretending to
 * fail. Polling stops on its own once a terminal status arrives.
 */
export function usePaymentStatus(paymentId: string | null, enabled = true) {
  return useQuery({
    queryKey: queryKeys.payments.status(paymentId ?? ''),
    queryFn: () => paymentEndpoints.status(paymentId!),
    enabled: Boolean(paymentId) && enabled,
    refetchInterval: (query) =>
      isTerminalPaymentStatus(query.state.data?.status) ? false : 2_500,
    refetchIntervalInBackground: false,
    staleTime: 0,
  });
}

export function usePayment(paymentId: string | null) {
  return useQuery({
    queryKey: queryKeys.payments.detail(paymentId ?? ''),
    queryFn: () => paymentEndpoints.get(paymentId!),
    enabled: Boolean(paymentId),
  });
}
