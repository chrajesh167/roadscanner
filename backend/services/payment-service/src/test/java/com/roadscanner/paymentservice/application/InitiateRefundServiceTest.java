package com.roadscanner.paymentservice.application;

import com.roadscanner.paymentservice.application.usecase.refund.InitiateRefundService;
import com.roadscanner.paymentservice.domain.exception.PaymentNotRefundableException;
import com.roadscanner.paymentservice.domain.exception.RefundAmountExceededException;
import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.model.RefundReason;
import com.roadscanner.paymentservice.domain.model.RefundStatus;
import com.roadscanner.paymentservice.domain.port.in.InitiateRefund;
import com.roadscanner.paymentservice.testsupport.fakes.FakePaymentGateway;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryPaymentRepository;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryRefundRepository;
import com.roadscanner.paymentservice.testsupport.fakes.NoOpAuditPort;
import com.roadscanner.paymentservice.testsupport.fakes.RecordingPaymentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitiateRefundServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private static final Currency INR = Currency.getInstance("INR");

    private InMemoryPaymentRepository payments;
    private InMemoryRefundRepository refunds;
    private RecordingPaymentEventPublisher publisher;
    private InitiateRefundService service;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        refunds = new InMemoryRefundRepository();
        publisher = new RecordingPaymentEventPublisher();
        service = new InitiateRefundService(payments, refunds, new FakePaymentGateway(), publisher,
                new NoOpAuditPort(), Clock.fixed(T0, ZoneOffset.UTC));
    }

    private Payment capturedPayment(BigDecimal amount) {
        Payment payment = Payment.create(PaymentId.generate(), new BookingReference(UUID.randomUUID()),
                UUID.randomUUID(), new Money(amount, INR), PaymentMethod.CARD, new GatewayType("FAKE"),
                new IdempotencyKey("pay-key-" + UUID.randomUUID()), T0.plusSeconds(600), T0);
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        payment.capture(null, T0.plusSeconds(10));
        return payments.save(payment);
    }

    @Test
    void fullRefundMovesPaymentToRefundPendingAndPublishesInitiated() {
        Payment payment = capturedPayment(BigDecimal.valueOf(500));
        InitiateRefund.Result result = service.initiate(new InitiateRefund.Command(payment.id(), null,
                RefundReason.TRAVELER_REQUESTED, new IdempotencyKey("refund-1")));

        assertThat(result.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(payments.findById(payment.id()).orElseThrow().status()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(publisher.published).anyMatch(e -> e.startsWith("RefundInitiated"));
    }

    @Test
    void refundIsIdempotentOnTheRefundKey() {
        Payment payment = capturedPayment(BigDecimal.valueOf(500));
        InitiateRefund.Command cmd = new InitiateRefund.Command(payment.id(), null, RefundReason.TRAVELER_REQUESTED,
                new IdempotencyKey("refund-1"));
        InitiateRefund.Result first = service.initiate(cmd);
        InitiateRefund.Result second = service.initiate(cmd);

        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.refundId()).isEqualTo(first.refundId());
        assertThat(refunds.findByPaymentId(payment.id())).hasSize(1);
    }

    @Test
    void refundOnAnUncapturedPaymentIsRejected() {
        Payment payment = Payment.create(PaymentId.generate(), new BookingReference(UUID.randomUUID()),
                UUID.randomUUID(), new Money(BigDecimal.valueOf(500), INR), PaymentMethod.CARD, new GatewayType("FAKE"),
                new IdempotencyKey("pk"), T0.plusSeconds(600), T0);
        payment.startAttempt(GatewayReference.ofPayment("o1", "p1"), T0);
        payments.save(payment);

        assertThatThrownBy(() -> service.initiate(new InitiateRefund.Command(payment.id(), null,
                RefundReason.TRAVELER_REQUESTED, new IdempotencyKey("refund-1"))))
                .isInstanceOf(PaymentNotRefundableException.class);
    }

    @Test
    void refundExceedingCapturedAmountIsRejected() {
        Payment payment = capturedPayment(BigDecimal.valueOf(500));
        assertThatThrownBy(() -> service.initiate(new InitiateRefund.Command(payment.id(),
                new Money(BigDecimal.valueOf(600), INR), RefundReason.TRAVELER_REQUESTED,
                new IdempotencyKey("refund-1"))))
                .isInstanceOf(RefundAmountExceededException.class);
    }
}
