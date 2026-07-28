package com.roadscanner.paymentservice.application;

import com.roadscanner.paymentservice.application.usecase.webhook.HandleGatewayWebhookService;
import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.port.in.HandleGatewayWebhook;
import com.roadscanner.paymentservice.testsupport.fakes.FakePaymentGateway;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryPaymentRepository;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryRefundRepository;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryTransactionLedger;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryWebhookEventRepository;
import com.roadscanner.paymentservice.testsupport.fakes.NoOpAuditPort;
import com.roadscanner.paymentservice.testsupport.fakes.RecordingPaymentEventPublisher;
import com.roadscanner.paymentservice.testsupport.fakes.RecordingReconciliationRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HandleGatewayWebhookServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private InMemoryPaymentRepository payments;
    private InMemoryTransactionLedger ledger;
    private RecordingPaymentEventPublisher publisher;
    private RecordingReconciliationRecorder reconciliation;
    private HandleGatewayWebhookService service;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        ledger = new InMemoryTransactionLedger();
        publisher = new RecordingPaymentEventPublisher();
        reconciliation = new RecordingReconciliationRecorder();
        service = new HandleGatewayWebhookService(new FakePaymentGateway(), new InMemoryWebhookEventRepository(),
                payments, new InMemoryRefundRepository(), ledger, publisher, new NoOpAuditPort(), reconciliation,
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    private Payment pendingPaymentWithGatewayId(String gatewayPaymentId) {
        Payment payment = Payment.create(PaymentId.generate(), new BookingReference(UUID.randomUUID()),
                UUID.randomUUID(), new Money(BigDecimal.valueOf(500), Currency.getInstance("INR")), PaymentMethod.UPI,
                FakePaymentGateway.TYPE, new IdempotencyKey("k-" + UUID.randomUUID()), T0.plusSeconds(600), T0);
        payment.startAttempt(GatewayReference.ofPayment("order", gatewayPaymentId), T0);
        return payments.save(payment);
    }

    @Test
    void captureWebhookCapturesThePaymentAndPublishesCompleted() {
        Payment payment = pendingPaymentWithGatewayId("pay-1");
        HandleGatewayWebhook.Result result = service.handle(
                new HandleGatewayWebhook.Command(FakePaymentGateway.TYPE, "pay-1", "sig"));

        assertThat(result.outcome()).isEqualTo(HandleGatewayWebhook.Outcome.APPLIED);
        assertThat(payments.findById(payment.id()).orElseThrow().status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(ledger.transactions).hasSize(1);
        assertThat(publisher.published).anyMatch(e -> e.startsWith("PaymentCompleted"));
    }

    @Test
    void duplicateWebhookIsIgnored() {
        pendingPaymentWithGatewayId("pay-1");
        service.handle(new HandleGatewayWebhook.Command(FakePaymentGateway.TYPE, "pay-1", "sig"));
        HandleGatewayWebhook.Result second = service.handle(
                new HandleGatewayWebhook.Command(FakePaymentGateway.TYPE, "pay-1", "sig"));

        assertThat(second.outcome()).isEqualTo(HandleGatewayWebhook.Outcome.DUPLICATE_IGNORED);
        assertThat(publisher.published).filteredOn(e -> e.startsWith("PaymentCompleted")).hasSize(1);
    }

    @Test
    void uncorrelatedWebhookIsFlaggedForReconciliation() {
        HandleGatewayWebhook.Result result = service.handle(
                new HandleGatewayWebhook.Command(FakePaymentGateway.TYPE, "unknown-pay", "sig"));

        assertThat(result.outcome()).isEqualTo(HandleGatewayWebhook.Outcome.UNCORRELATED);
        assertThat(reconciliation.discrepancies).anyMatch(d -> d.startsWith("UNCORRELATED_WEBHOOK"));
    }

    @Test
    void lateCaptureAfterExpiryRecordsMoneyButKeepsExpiredStatus() {
        Payment payment = pendingPaymentWithGatewayId("pay-1");
        payment.expire(T0.plusSeconds(601));
        payments.save(payment);

        HandleGatewayWebhook.Result result = service.handle(
                new HandleGatewayWebhook.Command(FakePaymentGateway.TYPE, "pay-1", "sig"));

        assertThat(result.outcome()).isEqualTo(HandleGatewayWebhook.Outcome.APPLIED);
        assertThat(payments.findById(payment.id()).orElseThrow().status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(ledger.transactions).hasSize(1); // money recorded for financial accuracy
        assertThat(publisher.published).anyMatch(e -> e.startsWith("PaymentCompleted"));
        assertThat(reconciliation.discrepancies).anyMatch(d -> d.startsWith("LATE_SUCCESS_AFTER_TIMEOUT"));
    }
}
