package com.roadscanner.paymentservice.application.usecase.refund;

import com.roadscanner.paymentservice.domain.exception.RefundNotFoundException;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.port.in.GetRefund;
import com.roadscanner.paymentservice.domain.port.out.RefundRepository;

/** Implements {@link GetRefund}. */
public class GetRefundService implements GetRefund {

    private final RefundRepository refundRepository;

    public GetRefundService(RefundRepository refundRepository) {
        this.refundRepository = refundRepository;
    }

    @Override
    public Result get(Command command) {
        Refund refund = refundRepository.findById(command.refundId())
                .filter(r -> r.paymentId().equals(command.paymentId()))
                .orElseThrow(() -> new RefundNotFoundException(command.refundId().toString()));
        return new Result(refund);
    }
}
