package com.roadscanner.paymentservice.domain.port.in;

import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundId;

import java.util.Objects;

/** {@code Get Refund} — track a refund's progress ({@code booking-service}/support, FR-8.3). */
public interface GetRefund {

    Result get(Command command);

    record Command(PaymentId paymentId, RefundId refundId) {
        public Command {
            Objects.requireNonNull(paymentId, "paymentId must not be null");
            Objects.requireNonNull(refundId, "refundId must not be null");
        }
    }

    record Result(Refund refund) {
    }
}
