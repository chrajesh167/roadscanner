import { paymentApi } from '../client';
import type {
  InitiatePaymentRequest,
  InitiatePaymentResponse,
  PaymentResponse,
  PaymentStatusResponse,
} from '../types';

/** payment-service — `adapter/in/rest/payment`. TRAVELER-only. */
export const paymentEndpoints = {
  /**
   * POST /api/v1/payments
   *
   * The `Idempotency-Key` header is mandatory (the controller declares it as required). The caller
   * owns the key and must reuse the *same* key when retrying the same intent — that is what makes
   * a double-submit return the existing payment (200) instead of charging twice (201).
   */
  async initiate(
    body: InitiatePaymentRequest,
    idempotencyKey: string,
  ): Promise<InitiatePaymentResponse> {
    const { data } = await paymentApi.post<InitiatePaymentResponse>('/api/v1/payments', body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return data;
  },

  /** GET /api/v1/payments/{paymentId} */
  async get(paymentId: string): Promise<PaymentResponse> {
    const { data } = await paymentApi.get<PaymentResponse>(`/api/v1/payments/${paymentId}`);
    return data;
  },

  /** GET /api/v1/payments/{paymentId}/status — the endpoint the payment screen polls. */
  async status(paymentId: string): Promise<PaymentStatusResponse> {
    const { data } = await paymentApi.get<PaymentStatusResponse>(
      `/api/v1/payments/${paymentId}/status`,
    );
    return data;
  },
};
