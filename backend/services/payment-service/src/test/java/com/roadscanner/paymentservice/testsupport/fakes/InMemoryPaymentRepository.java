package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<PaymentId, Payment> store = new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {
        store.put(payment.id(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(IdempotencyKey key) {
        return store.values().stream().filter(p -> p.idempotencyKey().equals(key)).findFirst();
    }

    @Override
    public Optional<Payment> findActiveByBookingReference(BookingReference bookingReference) {
        return store.values().stream()
                .filter(p -> p.bookingReference().equals(bookingReference) && !p.status().isTerminal())
                .findFirst();
    }

    @Override
    public Optional<Payment> findByBookingReference(BookingReference bookingReference) {
        return store.values().stream().filter(p -> p.bookingReference().equals(bookingReference)).findFirst();
    }

    @Override
    public Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId) {
        if (gatewayPaymentId == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(p -> gatewayPaymentId.equals(p.gatewayReference().gatewayPaymentId()))
                .findFirst();
    }

    @Override
    public List<Payment> findPreCaptureWithExpiryBefore(Instant cutoff) {
        return store.values().stream()
                .filter(p -> p.status().isPreCapture() && p.expiresAt().isBefore(cutoff))
                .toList();
    }
}
