package com.roadscanner.paymentservice.config;

import com.roadscanner.paymentservice.domain.port.in.SweepExpiredPayments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The scheduled job this service runs — a thin trigger bean; all logic lives in the
 * application-layer service (framework-free, independently testable), matching
 * {@code booking-service}'s {@code BookingSchedulers} pattern. */
@Component
public class PaymentSchedulers {

    private static final Logger log = LoggerFactory.getLogger(PaymentSchedulers.class);

    private final SweepExpiredPayments sweepExpiredPayments;

    public PaymentSchedulers(SweepExpiredPayments sweepExpiredPayments) {
        this.sweepExpiredPayments = sweepExpiredPayments;
    }

    @Scheduled(cron = "${roadscanner.payment.scheduling.sweep-expired-payments-cron}")
    public void sweepExpiredPayments() {
        try {
            SweepExpiredPayments.Result result = sweepExpiredPayments.sweep();
            if (result.expiredCount() > 0) {
                log.info("Swept {} expired payment(s) to EXPIRED", result.expiredCount());
            }
        } catch (RuntimeException e) {
            log.error("Sweep Expired Payments failed unexpectedly", e);
        }
    }
}
