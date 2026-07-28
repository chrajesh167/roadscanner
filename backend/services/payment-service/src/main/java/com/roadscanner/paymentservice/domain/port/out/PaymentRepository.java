package com.roadscanner.paymentservice.domain.port.out;

import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Payment} — the platform's only source of truth for payment state
 * (docs/services/payment-service/data-ownership.md). */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(PaymentId id);

    /** Idempotency surface: a repeat of the same client key returns the existing payment rather
     * than creating a second one (docs/services/payment-service/domain-model.md). */
    Optional<Payment> findByIdempotencyKey(IdempotencyKey key);

    /** Enforces "at most one non-terminal {@link Payment} per booking" — a retry reuses the
     * existing payment; it never creates a second live one (FR-4.3). */
    Optional<Payment> findActiveByBookingReference(BookingReference bookingReference);

    /** Backs the reconciliation consumer — locate the captured payment for a cancelled booking. */
    Optional<Payment> findByBookingReference(BookingReference bookingReference);

    /** Correlates an inbound webhook to its payment by the gateway's own payment id. */
    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    /** Backs {@code Sweep Expired Payments}. */
    List<Payment> findPreCaptureWithExpiryBefore(Instant cutoff);
}
