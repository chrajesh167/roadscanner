package com.roadscanner.paymentservice.domain.port.out;

import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundId;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Refund} — its own lifecycle, tracked here and nowhere else
 * ({@code booking-service} deliberately does not track refund state). */
public interface RefundRepository {

    Refund save(Refund refund);

    Optional<Refund> findById(RefundId id);

    /** Idempotency surface: a repeat of the same refund key returns the existing refund, never a
     * second one — the primary guard against duplicate refunds
     * (docs/services/payment-service/domain-model.md's invariants). */
    Optional<Refund> findByIdempotencyKey(IdempotencyKey key);

    /** All refunds for a payment — used to enforce the total-refunded-&le;-captured ceiling and to
     * answer the reconciliation "does a refund already exist" question. */
    List<Refund> findByPaymentId(PaymentId paymentId);

    /** Correlates an inbound refund webhook to its refund by the gateway's own refund id. */
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
}
