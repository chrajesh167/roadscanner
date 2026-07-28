package com.roadscanner.paymentservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private Payment newPayment() {
        return Payment.create(PaymentId.generate(), new BookingReference(UUID.randomUUID()), UUID.randomUUID(),
                new Money(BigDecimal.valueOf(500), Currency.getInstance("INR")), PaymentMethod.UPI,
                new GatewayType("RAZORPAY"), new IdempotencyKey("key-1"), T0.plusSeconds(600), T0);
    }

    @Test
    void createStartsInCreated() {
        Payment payment = newPayment();
        assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.attempts()).isEmpty();
        assertThat(payment.createdAt()).isEqualTo(T0);
    }

    @Test
    void startAttemptMovesToPendingAndRecordsAttempt() {
        Payment payment = newPayment();
        boolean applied = payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        assertThat(applied).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.attempts()).hasSize(1);
        assertThat(payment.gatewayReference().gatewayPaymentId()).isEqualTo("p1");
    }

    @Test
    void captureMovesToCapturedAndSucceedsAttempt() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        boolean applied = payment.capture(GatewayReference.ofPayment("o1", "p1"), T0.plusSeconds(10));
        assertThat(applied).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.capturedAt()).contains(T0.plusSeconds(10));
        assertThat(payment.attempts().get(0).outcome()).isEqualTo(AttemptOutcome.SUCCEEDED);
    }

    @Test
    void captureIsIdempotentNoOpWhenAlreadyCaptured() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        payment.capture(null, T0.plusSeconds(10));
        boolean secondCapture = payment.capture(null, T0.plusSeconds(20));
        assertThat(secondCapture).isFalse();
        assertThat(payment.capturedAt()).contains(T0.plusSeconds(10));
    }

    @Test
    void failMovesToFailed() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        boolean applied = payment.fail("DECLINED", "insufficient funds", T0.plusSeconds(5));
        assertThat(applied).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.status().isTerminal()).isTrue();
    }

    @Test
    void expireMovesToExpiredAndIsTerminal() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        assertThat(payment.isExpiredAsOf(T0.plusSeconds(601))).isTrue();
        boolean applied = payment.expire(T0.plusSeconds(601));
        assertThat(applied).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void captureDoesNotRevertExpiredStatus_lateSuccessEdgeCase() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        payment.expire(T0.plusSeconds(601));
        boolean lateCapture = payment.capture(null, T0.plusSeconds(700));
        assertThat(lateCapture).isFalse();
        assertThat(payment.status()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void fullRefundLifecycle() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        payment.capture(null, T0.plusSeconds(10));
        assertThat(payment.beginFullRefund()).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(payment.completeFullRefund()).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundFailureRevertsToCaptured() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        payment.capture(null, T0.plusSeconds(10));
        payment.beginFullRefund();
        assertThat(payment.revertFullRefund()).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void cannotBeginRefundBeforeCapture() {
        Payment payment = newPayment();
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        assertThat(payment.beginFullRefund()).isFalse();
    }
}
