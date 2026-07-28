package com.roadscanner.paymentservice.application.usecase.scheduled;

import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.port.in.SweepExpiredPayments;
import com.roadscanner.paymentservice.domain.port.out.PaymentEventPublisher;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implements {@link SweepExpiredPayments} — transitions pre-capture payments past their acceptable
 * window to {@code EXPIRED} and emits {@code PaymentTimedOut} (docs/architecture/payment-flow.md's
 * "Timeout"). A late gateway capture after this point is handled by the webhook handler's
 * late-success path.
 */
public class SweepExpiredPaymentsService implements SweepExpiredPayments {

    private static final Logger log = LoggerFactory.getLogger(SweepExpiredPaymentsService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;
    private final Clock clock;

    public SweepExpiredPaymentsService(PaymentRepository paymentRepository, PaymentEventPublisher eventPublisher,
                                       Clock clock) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Result sweep() {
        Instant now = clock.instant();
        List<Payment> expired = paymentRepository.findPreCaptureWithExpiryBefore(now);
        int count = 0;
        for (Payment payment : expired) {
            if (payment.expire(now)) {
                paymentRepository.save(payment);
                eventPublisher.publishPaymentTimedOut(payment, now);
                count++;
            }
        }
        if (count > 0) {
            log.info("Swept {} expired payment(s) to EXPIRED", count);
        }
        return new Result(count);
    }
}
