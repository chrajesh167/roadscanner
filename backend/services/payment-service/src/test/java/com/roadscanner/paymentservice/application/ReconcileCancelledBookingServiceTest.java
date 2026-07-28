package com.roadscanner.paymentservice.application;

import com.roadscanner.paymentservice.application.usecase.reconciliation.ReconcileCancelledBookingService;
import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.model.RefundReason;
import com.roadscanner.paymentservice.domain.port.in.ReconcileCancelledBooking;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryPaymentRepository;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryRefundRepository;
import com.roadscanner.paymentservice.testsupport.fakes.RecordingReconciliationRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileCancelledBookingServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private static final Currency INR = Currency.getInstance("INR");

    private InMemoryPaymentRepository payments;
    private InMemoryRefundRepository refunds;
    private RecordingReconciliationRecorder reconciliation;
    private ReconcileCancelledBookingService service;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        refunds = new InMemoryRefundRepository();
        reconciliation = new RecordingReconciliationRecorder();
        service = new ReconcileCancelledBookingService(payments, refunds, reconciliation);
    }

    private Payment capturedPaymentForBooking(BookingReference booking) {
        Payment payment = Payment.create(PaymentId.generate(), booking, UUID.randomUUID(),
                new Money(BigDecimal.valueOf(500), INR), PaymentMethod.CARD, new GatewayType("FAKE"),
                new IdempotencyKey("k-" + UUID.randomUUID()), T0.plusSeconds(600), T0);
        payment.startAttempt(GatewayReference.ofPayment("o", "p"), T0);
        payment.capture(null, T0.plusSeconds(10));
        return payments.save(payment);
    }

    @Test
    void capturedPaymentWithNoRefundIsFlaggedForSupport() {
        BookingReference booking = new BookingReference(UUID.randomUUID());
        capturedPaymentForBooking(booking);

        service.reconcile(new ReconcileCancelledBooking.Command(booking, "TRAVELER_REQUESTED", T0));

        assertThat(reconciliation.discrepancies).anyMatch(d -> d.startsWith("CANCELLED_BOOKING_WITHOUT_REFUND"));
    }

    @Test
    void capturedPaymentWithAnExistingRefundIsANoOp() {
        BookingReference booking = new BookingReference(UUID.randomUUID());
        Payment payment = capturedPaymentForBooking(booking);
        refunds.save(Refund.create(RefundId.generate(), payment.id(), booking,
                new Money(BigDecimal.valueOf(500), INR), true, RefundReason.TRAVELER_REQUESTED,
                new IdempotencyKey("r-1"), T0));

        service.reconcile(new ReconcileCancelledBooking.Command(booking, "TRAVELER_REQUESTED", T0));

        assertThat(reconciliation.discrepancies).isEmpty();
    }

    @Test
    void bookingWithNoPaymentIsANoOp() {
        service.reconcile(new ReconcileCancelledBooking.Command(new BookingReference(UUID.randomUUID()),
                "PAYMENT_FAILED", T0));
        assertThat(reconciliation.discrepancies).isEmpty();
    }
}
