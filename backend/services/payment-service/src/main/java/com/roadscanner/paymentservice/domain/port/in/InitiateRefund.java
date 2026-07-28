package com.roadscanner.paymentservice.domain.port.in;

import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.model.RefundReason;
import com.roadscanner.paymentservice.domain.model.RefundStatus;

import java.util.Objects;

/**
 * {@code Initiate Refund} — the <strong>authoritative</strong> refund trigger, called synchronously
 * by {@code booking-service} (service-to-service) or by admin/support override
 * (docs/services/payment-service/boundaries.md's "the Refund Trigger, Reconciled"). Executes the
 * {@code booking-service}-computed {@code amount}; {@code payment-service} never computes a policy.
 * A repeat of the same {@link IdempotencyKey} returns the existing refund — the primary guard
 * against duplicate refunds. {@code amount == null} means a full refund of the captured amount.
 */
public interface InitiateRefund {

    Result initiate(Command command);

    record Command(PaymentId paymentId, Money amount, RefundReason reason, IdempotencyKey idempotencyKey) {
        public Command {
            Objects.requireNonNull(paymentId, "paymentId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        }
    }

    record Result(RefundId refundId, RefundStatus status, boolean alreadyExisted) {
    }
}
