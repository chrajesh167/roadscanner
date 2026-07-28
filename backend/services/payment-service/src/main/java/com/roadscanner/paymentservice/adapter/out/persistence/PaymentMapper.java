package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.model.AttemptOutcome;
import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentAttempt;
import com.roadscanner.paymentservice.domain.model.PaymentAttemptId;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;

import java.util.Currency;
import java.util.List;

/** The only class that sees both {@code domain.model} and {@link PaymentJpaEntity} — matching
 * {@code booking-service}'s {@code BookingMapper} convention exactly. */
final class PaymentMapper {

    Payment toDomain(PaymentJpaEntity entity) {
        List<PaymentAttempt> attempts = entity.getAttempts().stream()
                .map(a -> PaymentAttempt.reconstitute(new PaymentAttemptId(a.getAttemptId()), a.getAttemptNumber(),
                        new GatewayReference(a.getGatewayPaymentId(), a.getGatewayOrderId(), a.getGatewayRefundId()),
                        AttemptOutcome.valueOf(a.getOutcome()), a.getFailureCode(), a.getFailureReason(),
                        a.getStartedAt(), a.getSettledAt()))
                .toList();
        return Payment.reconstitute(
                new PaymentId(entity.getId()),
                new BookingReference(entity.getBookingReference()),
                entity.getTravelerId(),
                new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency())),
                PaymentMethod.valueOf(entity.getMethod()),
                new GatewayType(entity.getGatewayType()),
                new GatewayReference(entity.getGatewayPaymentId(), entity.getGatewayOrderId(),
                        entity.getGatewayRefundId()),
                PaymentStatus.valueOf(entity.getStatus()),
                new IdempotencyKey(entity.getIdempotencyKey()),
                attempts,
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getAuthorizedAt(),
                entity.getCapturedAt(),
                entity.getFailedAt(),
                entity.getCancelledAt(),
                entity.getExpiredAt());
    }

    PaymentJpaEntity toNewEntity(Payment payment) {
        GatewayReference ref = payment.gatewayReference();
        return new PaymentJpaEntity(
                payment.id().value(),
                payment.bookingReference().value(),
                payment.travelerId(),
                payment.amount().amount(),
                payment.amount().currency().getCurrencyCode(),
                payment.method().name(),
                payment.gatewayType().code(),
                ref.gatewayPaymentId(),
                ref.gatewayOrderId(),
                ref.gatewayRefundId(),
                payment.status().name(),
                payment.idempotencyKey().value(),
                toAttemptEmbeddables(payment.attempts()),
                payment.expiresAt(),
                payment.createdAt(),
                payment.authorizedAt().orElse(null),
                payment.capturedAt().orElse(null),
                payment.failedAt().orElse(null),
                payment.cancelledAt().orElse(null),
                payment.expiredAt().orElse(null));
    }

    void applyTo(PaymentJpaEntity entity, Payment payment) {
        GatewayReference ref = payment.gatewayReference();
        entity.applyMutableState(ref.gatewayPaymentId(), ref.gatewayOrderId(), ref.gatewayRefundId(),
                payment.status().name(), toAttemptEmbeddables(payment.attempts()),
                payment.authorizedAt().orElse(null), payment.capturedAt().orElse(null),
                payment.failedAt().orElse(null), payment.cancelledAt().orElse(null),
                payment.expiredAt().orElse(null));
    }

    private List<PaymentAttemptEmbeddable> toAttemptEmbeddables(List<PaymentAttempt> attempts) {
        return attempts.stream()
                .map(a -> new PaymentAttemptEmbeddable(a.id().value(), a.attemptNumber(),
                        a.gatewayReference().gatewayPaymentId(), a.gatewayReference().gatewayOrderId(),
                        a.gatewayReference().gatewayRefundId(), a.outcome().name(),
                        a.failureCode().orElse(null), a.failureReason().orElse(null), a.startedAt(),
                        a.settledAt().orElse(null)))
                .toList();
    }
}
