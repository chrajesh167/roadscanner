package com.roadscanner.paymentservice.application.usecase.payment;

import com.roadscanner.paymentservice.domain.exception.PaymentNotFoundException;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.port.in.GetPaymentStatus;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;

/** Implements {@link GetPaymentStatus}. */
public class GetPaymentStatusService implements GetPaymentStatus {

    private final PaymentRepository paymentRepository;

    public GetPaymentStatusService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Result get(Command command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .filter(p -> command.requester().isPrivileged() || p.isOwnedBy(command.requester().requesterId()))
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId().toString()));
        return new Result(payment.id(), payment.status());
    }
}
