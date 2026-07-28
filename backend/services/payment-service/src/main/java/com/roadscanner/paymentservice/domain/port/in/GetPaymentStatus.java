package com.roadscanner.paymentservice.domain.port.in;

import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.model.RequesterContext;

import java.util.Objects;

/** {@code Get Payment Status} — supports the async (UPI/webhook) case where the client polls for an
 * outcome that arrives out-of-band (docs/services/payment-service/use-cases.md). */
public interface GetPaymentStatus {

    Result get(Command command);

    record Command(PaymentId paymentId, RequesterContext requester) {
        public Command {
            Objects.requireNonNull(paymentId, "paymentId must not be null");
            Objects.requireNonNull(requester, "requester must not be null");
        }
    }

    record Result(PaymentId paymentId, PaymentStatus status) {
    }
}
