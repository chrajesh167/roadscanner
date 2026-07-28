package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.model.AttemptOutcome;
import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundAttempt;
import com.roadscanner.paymentservice.domain.model.RefundAttemptId;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.model.RefundReason;
import com.roadscanner.paymentservice.domain.model.RefundStatus;

import java.util.Currency;
import java.util.List;

final class RefundMapper {

    Refund toDomain(RefundJpaEntity entity) {
        List<RefundAttempt> attempts = entity.getAttempts().stream()
                .map(a -> RefundAttempt.reconstitute(new RefundAttemptId(a.getAttemptId()), a.getAttemptNumber(),
                        GatewayReference.ofRefund(a.getGatewayRefundId()), AttemptOutcome.valueOf(a.getOutcome()),
                        a.getFailureCode(), a.getFailureReason(), a.getStartedAt(), a.getSettledAt()))
                .toList();
        return Refund.reconstitute(
                new RefundId(entity.getId()),
                new PaymentId(entity.getPaymentId()),
                new BookingReference(entity.getBookingReference()),
                new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency())),
                entity.isFullRefund(),
                RefundReason.valueOf(entity.getReason()),
                RefundStatus.valueOf(entity.getStatus()),
                new IdempotencyKey(entity.getIdempotencyKey()),
                GatewayReference.ofRefund(entity.getGatewayRefundId()),
                attempts,
                entity.getCreatedAt(),
                entity.getCompletedAt(),
                entity.getFailedAt());
    }

    RefundJpaEntity toNewEntity(Refund refund) {
        return new RefundJpaEntity(
                refund.id().value(),
                refund.paymentId().value(),
                refund.bookingReference().value(),
                refund.amount().amount(),
                refund.amount().currency().getCurrencyCode(),
                refund.isFullRefund(),
                refund.reason().name(),
                refund.status().name(),
                refund.idempotencyKey().value(),
                refund.gatewayReference().gatewayRefundId(),
                toAttemptEmbeddables(refund.attempts()),
                refund.createdAt(),
                refund.completedAt().orElse(null),
                refund.failedAt().orElse(null));
    }

    void applyTo(RefundJpaEntity entity, Refund refund) {
        entity.applyMutableState(refund.status().name(), refund.gatewayReference().gatewayRefundId(),
                toAttemptEmbeddables(refund.attempts()), refund.completedAt().orElse(null),
                refund.failedAt().orElse(null));
    }

    private List<RefundAttemptEmbeddable> toAttemptEmbeddables(List<RefundAttempt> attempts) {
        return attempts.stream()
                .map(a -> new RefundAttemptEmbeddable(a.id().value(), a.attemptNumber(),
                        a.gatewayReference().gatewayRefundId(), a.outcome().name(), a.failureCode().orElse(null),
                        a.failureReason().orElse(null), a.startedAt(), a.settledAt().orElse(null)))
                .toList();
    }
}
