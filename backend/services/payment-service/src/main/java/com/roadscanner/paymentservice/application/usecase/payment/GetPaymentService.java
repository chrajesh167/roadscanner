package com.roadscanner.paymentservice.application.usecase.payment;

import com.roadscanner.paymentservice.domain.exception.PaymentNotFoundException;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.port.in.GetPayment;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;

/** Implements {@link GetPayment}. Unauthorized access reads as 404, not 403 — the same
 * enumeration-protection posture {@code booking-service} applies
 * (docs/services/payment-service/boundaries.md). */
public class GetPaymentService implements GetPayment {

    private final PaymentRepository paymentRepository;

    public GetPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Result get(Command command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .filter(p -> command.requester().isPrivileged() || p.isOwnedBy(command.requester().requesterId()))
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId().toString()));
        return new Result(payment);
    }
}
