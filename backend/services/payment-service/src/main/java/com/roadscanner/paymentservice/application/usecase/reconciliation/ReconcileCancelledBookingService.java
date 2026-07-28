package com.roadscanner.paymentservice.application.usecase.reconciliation;

import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundStatus;
import com.roadscanner.paymentservice.domain.port.in.ReconcileCancelledBooking;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;
import com.roadscanner.paymentservice.domain.port.out.ReconciliationRecorder;
import com.roadscanner.paymentservice.domain.port.out.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Implements {@link ReconcileCancelledBooking} — consumes {@code BookingCancelled} for
 * <strong>reconciliation only</strong>. It never initiates a refund (it cannot compute the
 * policy-based amount); it verifies that a refund-eligible cancellation whose payment was captured
 * already has a corresponding {@code Refund}, and flags a discrepancy for support if not
 * (docs/services/payment-service/events-consumed.md's "The Refund-Trigger Reconciliation"). Keyed by
 * booking reference, so it can never create a second refund.
 */
public class ReconcileCancelledBookingService implements ReconcileCancelledBooking {

    private static final Logger log = LoggerFactory.getLogger(ReconcileCancelledBookingService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ReconciliationRecorder reconciliationRecorder;

    public ReconcileCancelledBookingService(PaymentRepository paymentRepository, RefundRepository refundRepository,
                                            ReconciliationRecorder reconciliationRecorder) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.reconciliationRecorder = reconciliationRecorder;
    }

    @Override
    public void reconcile(Command command) {
        Optional<Payment> found = paymentRepository.findByBookingReference(command.bookingReference());
        if (found.isEmpty()) {
            return; // no payment for this booking — nothing to reconcile
        }
        Payment payment = found.get();
        boolean moneyMoved = payment.status() == PaymentStatus.CAPTURED
                || payment.status() == PaymentStatus.REFUND_PENDING
                || payment.status() == PaymentStatus.REFUNDED;
        if (!moneyMoved) {
            return; // payment never captured — no refund owed
        }

        List<Refund> refunds = refundRepository.findByPaymentId(payment.id());
        boolean hasLiveRefund = refunds.stream().anyMatch(r -> r.status() != RefundStatus.FAILED);
        if (hasLiveRefund) {
            return; // a refund already exists (initiated via the authoritative sync API) — no-op
        }

        log.warn("Cancelled booking {} has a captured payment {} but no refund — flagging for support",
                command.bookingReference(), payment.id());
        reconciliationRecorder.recordDiscrepancy("CANCELLED_BOOKING_WITHOUT_REFUND", payment.id().toString(),
                "booking " + command.bookingReference() + " cancelled (" + command.cancellationReason()
                        + ") with a captured payment and no refund", command.occurredAt());
    }
}
