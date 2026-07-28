package com.roadscanner.paymentservice.application.usecase.refund;

import com.roadscanner.paymentservice.domain.exception.PaymentNotFoundException;
import com.roadscanner.paymentservice.domain.exception.PaymentNotRefundableException;
import com.roadscanner.paymentservice.domain.exception.RefundAmountExceededException;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.port.in.InitiateRefund;
import com.roadscanner.paymentservice.domain.port.out.PaymentAuditPort;
import com.roadscanner.paymentservice.domain.port.out.PaymentEventPublisher;
import com.roadscanner.paymentservice.domain.port.out.PaymentGateway;
import com.roadscanner.paymentservice.domain.port.out.PaymentGatewayRegistry;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;
import com.roadscanner.paymentservice.domain.port.out.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implements {@link InitiateRefund} — the authoritative refund trigger. Idempotent on the refund
 * key (primary guard against duplicate refunds); bounded by the total-refunded-&le;-captured ceiling
 * (second guard) — together these make duplicate refunds impossible
 * (docs/services/payment-service/domain-model.md's invariants). Executes the
 * {@code booking-service}-computed amount; never computes a policy.
 */
public class InitiateRefundService implements InitiateRefund {

    private static final Logger log = LoggerFactory.getLogger(InitiateRefundService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAuditPort auditPort;
    private final Clock clock;

    public InitiateRefundService(PaymentRepository paymentRepository, RefundRepository refundRepository,
                                 PaymentGatewayRegistry gatewayRegistry, PaymentEventPublisher eventPublisher,
                                 PaymentAuditPort auditPort, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.eventPublisher = eventPublisher;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Override
    public Result initiate(Command command) {
        Optional<Refund> existing = refundRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            Refund refund = existing.get();
            return new Result(refund.id(), refund.status(), true);
        }

        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId().toString()));
        if (payment.status() != PaymentStatus.CAPTURED && payment.status() != PaymentStatus.REFUND_PENDING
                && payment.status() != PaymentStatus.REFUNDED) {
            throw new PaymentNotRefundableException(payment.id().toString(), payment.status().name());
        }

        List<Refund> priorRefunds = refundRepository.findByPaymentId(payment.id());
        Money alreadyRefunded = sumNonFailed(priorRefunds, payment.amount().currency().getCurrencyCode());
        Money requested = command.amount() != null
                ? command.amount()
                : remaining(payment.amount(), alreadyRefunded);

        if (alreadyRefunded.plus(requested).isGreaterThan(payment.amount())) {
            throw new RefundAmountExceededException(payment.id().toString());
        }

        boolean fullRefund = alreadyRefunded.amount().signum() == 0
                && requested.amount().compareTo(payment.amount().amount()) == 0;

        Instant now = clock.instant();
        Refund refund = Refund.create(RefundId.generate(), payment.id(), payment.bookingReference(), requested,
                fullRefund, command.reason(), command.idempotencyKey(), now);

        if (fullRefund) {
            payment.beginFullRefund();
            paymentRepository.save(payment);
        }

        PaymentGateway gateway = gatewayRegistry.resolve(payment.gatewayType());
        GatewayReference refundReference = gateway.refund(new PaymentGateway.RefundRequest(
                refund.id(), payment.id(), payment.gatewayReference(), requested));
        refund.markProcessing(refundReference, now);

        Refund saved = refundRepository.save(refund);
        eventPublisher.publishRefundInitiated(saved, now);
        auditPort.record("REFUND_INITIATED", saved.id().toString(),
                "amount=" + requested.amount() + " " + requested.currency().getCurrencyCode()
                        + " reason=" + command.reason(), now);
        log.info("Initiated refund {} for payment {} (full={})", saved.id(), payment.id(), fullRefund);
        return new Result(saved.id(), saved.status(), false);
    }

    private Money sumNonFailed(List<Refund> refunds, String currencyCode) {
        BigDecimal total = refunds.stream()
                .filter(r -> r.status() != com.roadscanner.paymentservice.domain.model.RefundStatus.FAILED)
                .map(r -> r.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Money(total, java.util.Currency.getInstance(currencyCode));
    }

    private Money remaining(Money captured, Money alreadyRefunded) {
        return new Money(captured.amount().subtract(alreadyRefunded.amount()), captured.currency());
    }
}
