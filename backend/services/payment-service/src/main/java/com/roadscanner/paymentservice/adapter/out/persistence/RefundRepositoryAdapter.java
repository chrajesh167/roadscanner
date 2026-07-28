package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.port.out.RefundRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class RefundRepositoryAdapter implements RefundRepository {

    private final RefundSpringDataRepository springDataRepository;
    private final RefundMapper mapper = new RefundMapper();

    RefundRepositoryAdapter(RefundSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Refund save(Refund refund) {
        RefundJpaEntity entity = springDataRepository.findById(refund.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, refund);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(refund));
        return mapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Refund> findById(RefundId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Refund> findByIdempotencyKey(IdempotencyKey key) {
        return springDataRepository.findByIdempotencyKey(key.value()).map(mapper::toDomain);
    }

    @Override
    public List<Refund> findByPaymentId(PaymentId paymentId) {
        return springDataRepository.findByPaymentId(paymentId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Refund> findByGatewayRefundId(String gatewayRefundId) {
        if (gatewayRefundId == null) {
            return Optional.empty();
        }
        return springDataRepository.findByGatewayRefundId(gatewayRefundId).map(mapper::toDomain);
    }
}
