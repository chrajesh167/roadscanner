package com.roadscanner.paymentservice.config;

import com.roadscanner.paymentservice.application.usecase.payment.GetPaymentService;
import com.roadscanner.paymentservice.application.usecase.payment.GetPaymentStatusService;
import com.roadscanner.paymentservice.application.usecase.payment.InitiatePaymentService;
import com.roadscanner.paymentservice.application.usecase.reconciliation.ReconcileCancelledBookingService;
import com.roadscanner.paymentservice.application.usecase.refund.GetRefundService;
import com.roadscanner.paymentservice.application.usecase.refund.InitiateRefundService;
import com.roadscanner.paymentservice.application.usecase.scheduled.SweepExpiredPaymentsService;
import com.roadscanner.paymentservice.application.usecase.webhook.HandleGatewayWebhookService;
import com.roadscanner.paymentservice.domain.port.in.GetPayment;
import com.roadscanner.paymentservice.domain.port.in.GetPaymentStatus;
import com.roadscanner.paymentservice.domain.port.in.GetRefund;
import com.roadscanner.paymentservice.domain.port.in.HandleGatewayWebhook;
import com.roadscanner.paymentservice.domain.port.in.InitiatePayment;
import com.roadscanner.paymentservice.domain.port.in.InitiateRefund;
import com.roadscanner.paymentservice.domain.port.in.ReconcileCancelledBooking;
import com.roadscanner.paymentservice.domain.port.in.SweepExpiredPayments;
import com.roadscanner.paymentservice.domain.port.out.PaymentAuditPort;
import com.roadscanner.paymentservice.domain.port.out.PaymentEventPublisher;
import com.roadscanner.paymentservice.domain.port.out.PaymentGatewayRegistry;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;
import com.roadscanner.paymentservice.domain.port.out.ReconciliationRecorder;
import com.roadscanner.paymentservice.domain.port.out.RefundRepository;
import com.roadscanner.paymentservice.domain.port.out.TransactionLedger;
import com.roadscanner.paymentservice.domain.port.out.WebhookEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Explicit bean wiring for every application-layer use case — plain constructors, no Spring
 * stereotype annotations on the application classes themselves, matching every other service's
 * identical {@code UseCaseConfig} convention. */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public InitiatePayment initiatePayment(PaymentRepository paymentRepository, PaymentGatewayRegistry gatewayRegistry,
                                           PaymentEventPublisher eventPublisher, Clock clock,
                                           PaymentProperties properties) {
        return new InitiatePaymentService(paymentRepository, gatewayRegistry, eventPublisher, clock,
                properties.paymentWindow());
    }

    @Bean
    public GetPayment getPayment(PaymentRepository paymentRepository) {
        return new GetPaymentService(paymentRepository);
    }

    @Bean
    public GetPaymentStatus getPaymentStatus(PaymentRepository paymentRepository) {
        return new GetPaymentStatusService(paymentRepository);
    }

    @Bean
    public InitiateRefund initiateRefund(PaymentRepository paymentRepository, RefundRepository refundRepository,
                                         PaymentGatewayRegistry gatewayRegistry, PaymentEventPublisher eventPublisher,
                                         PaymentAuditPort auditPort, Clock clock) {
        return new InitiateRefundService(paymentRepository, refundRepository, gatewayRegistry, eventPublisher,
                auditPort, clock);
    }

    @Bean
    public GetRefund getRefund(RefundRepository refundRepository) {
        return new GetRefundService(refundRepository);
    }

    @Bean
    public HandleGatewayWebhook handleGatewayWebhook(PaymentGatewayRegistry gatewayRegistry,
                                                     WebhookEventRepository webhookEventRepository,
                                                     PaymentRepository paymentRepository,
                                                     RefundRepository refundRepository,
                                                     TransactionLedger transactionLedger,
                                                     PaymentEventPublisher eventPublisher, PaymentAuditPort auditPort,
                                                     ReconciliationRecorder reconciliationRecorder, Clock clock) {
        return new HandleGatewayWebhookService(gatewayRegistry, webhookEventRepository, paymentRepository,
                refundRepository, transactionLedger, eventPublisher, auditPort, reconciliationRecorder, clock);
    }

    @Bean
    public ReconcileCancelledBooking reconcileCancelledBooking(PaymentRepository paymentRepository,
                                                               RefundRepository refundRepository,
                                                               ReconciliationRecorder reconciliationRecorder) {
        return new ReconcileCancelledBookingService(paymentRepository, refundRepository, reconciliationRecorder);
    }

    @Bean
    public SweepExpiredPayments sweepExpiredPayments(PaymentRepository paymentRepository,
                                                     PaymentEventPublisher eventPublisher, Clock clock) {
        return new SweepExpiredPaymentsService(paymentRepository, eventPublisher, clock);
    }
}
