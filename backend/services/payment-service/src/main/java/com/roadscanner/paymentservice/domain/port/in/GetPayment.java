package com.roadscanner.paymentservice.domain.port.in;

import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.RequesterContext;

import java.util.Objects;

/** {@code Get Payment} — ownership-checked; a denied request reads as 404, not 403
 * (docs/services/payment-service/boundaries.md's "Payment &harr; Auth"). */
public interface GetPayment {

    Result get(Command command);

    record Command(PaymentId paymentId, RequesterContext requester) {
        public Command {
            Objects.requireNonNull(paymentId, "paymentId must not be null");
            Objects.requireNonNull(requester, "requester must not be null");
        }
    }

    record Result(Payment payment) {
    }
}
