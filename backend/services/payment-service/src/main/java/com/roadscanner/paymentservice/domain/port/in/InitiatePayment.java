package com.roadscanner.paymentservice.domain.port.in;

import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code Initiate Payment} — creates a {@code Payment} for a {@code PENDING_PAYMENT} booking and
 * starts the gateway transaction (docs/services/payment-service/use-cases.md). The client calls
 * this service directly, a frozen platform decision (docs/architecture/booking-flow.md step 3). A
 * repeat of the same {@link IdempotencyKey} returns the existing payment; a retry against a booking
 * whose previous payment is terminal creates a fresh one.
 */
public interface InitiatePayment {

    Result initiate(Command command);

    record Command(BookingReference bookingReference, UUID travelerId, Money amount, PaymentMethod method,
                   GatewayType gatewayType, IdempotencyKey idempotencyKey) {
        public Command {
            Objects.requireNonNull(bookingReference, "bookingReference must not be null");
            Objects.requireNonNull(travelerId, "travelerId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(method, "method must not be null");
            Objects.requireNonNull(gatewayType, "gatewayType must not be null");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        }
    }

    record Result(PaymentId paymentId, PaymentStatus status, boolean alreadyExisted) {
    }
}
