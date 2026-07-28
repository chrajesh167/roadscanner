package com.roadscanner.paymentservice.application.usecase.webhook;

import com.roadscanner.paymentservice.domain.exception.WebhookVerificationException;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayWebhookEvent;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.model.PaymentTransaction;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.port.in.HandleGatewayWebhook;
import com.roadscanner.paymentservice.domain.port.out.PaymentAuditPort;
import com.roadscanner.paymentservice.domain.port.out.PaymentEventPublisher;
import com.roadscanner.paymentservice.domain.port.out.PaymentGateway;
import com.roadscanner.paymentservice.domain.port.out.PaymentGatewayRegistry;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;
import com.roadscanner.paymentservice.domain.port.out.ReconciliationRecorder;
import com.roadscanner.paymentservice.domain.port.out.RefundRepository;
import com.roadscanner.paymentservice.domain.port.out.TransactionLedger;
import com.roadscanner.paymentservice.domain.port.out.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Implements {@link HandleGatewayWebhook} — verify signature, replay-check, correlate, apply,
 * idempotently (docs/services/payment-service/domain-model.md's "Webhook Verification"). Handles the
 * required late-success-after-timeout reconciliation path: a capture webhook arriving after the
 * payment already {@code EXPIRED} still records the money movement (ledger + {@code PaymentCompleted})
 * for financial accuracy, without reverting the terminal {@code EXPIRED} status
 * (docs/services/payment-service/payment-state-machine.md's "The Late-Success-After-Timeout
 * Interaction"). {@code booking-service} then turns that into an automatic refund.
 */
public class HandleGatewayWebhookService implements HandleGatewayWebhook {

    private static final Logger log = LoggerFactory.getLogger(HandleGatewayWebhookService.class);

    private final PaymentGatewayRegistry gatewayRegistry;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final TransactionLedger transactionLedger;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAuditPort auditPort;
    private final ReconciliationRecorder reconciliationRecorder;
    private final Clock clock;

    public HandleGatewayWebhookService(PaymentGatewayRegistry gatewayRegistry,
                                       WebhookEventRepository webhookEventRepository,
                                       PaymentRepository paymentRepository, RefundRepository refundRepository,
                                       TransactionLedger transactionLedger, PaymentEventPublisher eventPublisher,
                                       PaymentAuditPort auditPort, ReconciliationRecorder reconciliationRecorder,
                                       Clock clock) {
        this.gatewayRegistry = gatewayRegistry;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.transactionLedger = transactionLedger;
        this.eventPublisher = eventPublisher;
        this.auditPort = auditPort;
        this.reconciliationRecorder = reconciliationRecorder;
        this.clock = clock;
    }

    @Override
    public Result handle(Command command) {
        Instant now = clock.instant();
        PaymentGateway gateway = gatewayRegistry.resolve(command.gatewayType());

        if (!gateway.verifyWebhookSignature(command.rawPayload(), command.signatureHeader())) {
            auditPort.record("WEBHOOK_SIGNATURE_REJECTED", command.gatewayType().toString(),
                    "signature verification failed", now);
            throw new WebhookVerificationException("Webhook signature verification failed for gateway "
                    + command.gatewayType());
        }

        GatewayWebhookEvent event = gateway.parseWebhook(command.rawPayload());

        if (webhookEventRepository.existsByGatewayEventId(event.gatewayType(), event.gatewayEventId())) {
            log.debug("Duplicate webhook {} for {} — ignoring", event.gatewayEventId(), event.gatewayType());
            return new Result(Outcome.DUPLICATE_IGNORED);
        }

        Outcome outcome = apply(event, now);
        webhookEventRepository.record(event.gatewayType(), event.gatewayEventId(), true,
                digest(command.rawPayload()), outcome.name(), now);
        auditPort.record("WEBHOOK_RECEIVED", event.gatewayEventId(),
                "type=" + event.type() + " outcome=" + outcome, now);
        return new Result(outcome);
    }

    private Outcome apply(GatewayWebhookEvent event, Instant now) {
        return switch (event.type()) {
            case PAYMENT_AUTHORIZED -> applyToPayment(event, now, (payment) -> {
                payment.authorize(now);
                paymentRepository.save(payment);
            });
            case PAYMENT_CAPTURED -> applyCapture(event, now);
            case PAYMENT_FAILED -> applyToPayment(event, now, (payment) -> {
                if (payment.fail(event.failureCode(), event.failureReason(), now)) {
                    paymentRepository.save(payment);
                    eventPublisher.publishPaymentFailed(payment, now);
                }
            });
            case REFUND_COMPLETED -> applyRefundCompleted(event, now);
            case REFUND_FAILED -> applyRefundFailed(event, now);
            case UNKNOWN -> Outcome.UNCORRELATED;
        };
    }

    private Outcome applyCapture(GatewayWebhookEvent event, Instant now) {
        Optional<Payment> found = paymentRepository.findByGatewayPaymentId(event.gatewayPaymentId());
        if (found.isEmpty()) {
            return uncorrelated("payment", event.gatewayPaymentId(), now);
        }
        Payment payment = found.get();
        if (payment.capture(GatewayReference.ofPayment(payment.gatewayReference().gatewayOrderId(),
                event.gatewayPaymentId()), now)) {
            transactionLedger.append(PaymentTransaction.capture(payment.id(), payment.amount(),
                    payment.gatewayReference(), now));
            paymentRepository.save(payment);
            eventPublisher.publishPaymentCompleted(payment, now);
            return Outcome.APPLIED;
        }
        if (payment.status() == PaymentStatus.EXPIRED) {
            // Late success after a timeout-driven expiry — record the money movement truthfully and
            // emit PaymentCompleted; status stays EXPIRED (terminal). booking-service will refund.
            transactionLedger.append(PaymentTransaction.capture(payment.id(), payment.amount(),
                    payment.gatewayReference(), now));
            eventPublisher.publishPaymentCompleted(payment, now);
            auditPort.record("LATE_SUCCESS_AFTER_TIMEOUT", payment.id().toString(),
                    "capture arrived after EXPIRED — money recorded, refund expected", now);
            reconciliationRecorder.recordDiscrepancy("LATE_SUCCESS_AFTER_TIMEOUT", payment.id().toString(),
                    "gateway captured an already-expired payment", now);
            return Outcome.APPLIED;
        }
        return Outcome.APPLIED; // already CAPTURED/terminal — idempotent no-op beyond dedupe
    }

    private Outcome applyRefundCompleted(GatewayWebhookEvent event, Instant now) {
        Optional<Refund> found = refundRepository.findByGatewayRefundId(event.gatewayRefundId());
        if (found.isEmpty()) {
            return uncorrelated("refund", event.gatewayRefundId(), now);
        }
        Refund refund = found.get();
        if (refund.complete(GatewayReference.ofRefund(event.gatewayRefundId()), now)) {
            if (refund.isFullRefund()) {
                paymentRepository.findById(refund.paymentId()).ifPresent(payment -> {
                    payment.completeFullRefund();
                    paymentRepository.save(payment);
                });
            }
            transactionLedger.append(PaymentTransaction.refund(refund.paymentId(), refund.id(), refund.amount(),
                    refund.gatewayReference(), now));
            refundRepository.save(refund);
            eventPublisher.publishRefundCompleted(refund, now);
        }
        return Outcome.APPLIED;
    }

    private Outcome applyRefundFailed(GatewayWebhookEvent event, Instant now) {
        Optional<Refund> found = refundRepository.findByGatewayRefundId(event.gatewayRefundId());
        if (found.isEmpty()) {
            return uncorrelated("refund", event.gatewayRefundId(), now);
        }
        Refund refund = found.get();
        if (refund.fail(event.failureCode(), event.failureReason(), now)) {
            if (refund.isFullRefund()) {
                paymentRepository.findById(refund.paymentId()).ifPresent(payment -> {
                    payment.revertFullRefund();
                    paymentRepository.save(payment);
                });
            }
            refundRepository.save(refund);
            eventPublisher.publishRefundFailed(refund, now);
        }
        return Outcome.APPLIED;
    }

    private Outcome applyToPayment(GatewayWebhookEvent event, Instant now, java.util.function.Consumer<Payment> action) {
        Optional<Payment> found = paymentRepository.findByGatewayPaymentId(event.gatewayPaymentId());
        if (found.isEmpty()) {
            return uncorrelated("payment", event.gatewayPaymentId(), now);
        }
        action.accept(found.get());
        return Outcome.APPLIED;
    }

    private Outcome uncorrelated(String kind, String reference, Instant now) {
        log.warn("Webhook references unknown {} {} — flagged for reconciliation", kind, reference);
        reconciliationRecorder.recordDiscrepancy("UNCORRELATED_WEBHOOK", String.valueOf(reference),
                "no " + kind + " found for gateway reference", now);
        return Outcome.UNCORRELATED;
    }

    private String digest(String rawPayload) {
        return Integer.toHexString(rawPayload.hashCode());
    }
}
