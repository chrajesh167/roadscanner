package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Fetches-then-mutates on {@link #save}, matching {@code booking-service}'s optimistic-locking
 * rationale: a fresh-entity save would hand Hibernate no {@code @Version} read, bypassing the check
 * that guards two concurrently-processed triggers for the same payment (e.g. a capture webhook
 * racing a timeout sweep) from clobbering each other
 * (docs/services/payment-service/domain-model.md's "Concurrency"). */
@Repository
class PaymentRepositoryAdapter implements PaymentRepository {

    private static final List<String> TERMINAL = List.of(PaymentStatus.FAILED.name(), PaymentStatus.CANCELLED.name(),
            PaymentStatus.EXPIRED.name(), PaymentStatus.REFUNDED.name());
    private static final List<String> PRE_CAPTURE = List.of(PaymentStatus.CREATED.name(),
            PaymentStatus.PENDING.name(), PaymentStatus.AUTHORIZED.name());

    private final PaymentSpringDataRepository springDataRepository;
    private final PaymentMapper mapper = new PaymentMapper();

    PaymentRepositoryAdapter(PaymentSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = springDataRepository.findById(payment.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, payment);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(payment));
        return mapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(IdempotencyKey key) {
        return springDataRepository.findByIdempotencyKey(key.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findActiveByBookingReference(BookingReference bookingReference) {
        return springDataRepository.findByBookingReferenceAndStatusNotIn(bookingReference.value(), TERMINAL)
                .stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByBookingReference(BookingReference bookingReference) {
        return springDataRepository.findByBookingReference(bookingReference.value())
                .stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId) {
        if (gatewayPaymentId == null) {
            return Optional.empty();
        }
        return springDataRepository.findByGatewayPaymentId(gatewayPaymentId).map(mapper::toDomain);
    }

    @Override
    public List<Payment> findPreCaptureWithExpiryBefore(Instant cutoff) {
        return springDataRepository.findByStatusInAndExpiresAtBefore(PRE_CAPTURE, cutoff)
                .stream().map(mapper::toDomain).toList();
    }
}
