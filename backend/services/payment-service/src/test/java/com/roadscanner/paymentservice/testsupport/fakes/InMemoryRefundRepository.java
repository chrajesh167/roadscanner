package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.port.out.RefundRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRefundRepository implements RefundRepository {

    private final Map<RefundId, Refund> store = new ConcurrentHashMap<>();

    @Override
    public Refund save(Refund refund) {
        store.put(refund.id(), refund);
        return refund;
    }

    @Override
    public Optional<Refund> findById(RefundId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Refund> findByIdempotencyKey(IdempotencyKey key) {
        return store.values().stream().filter(r -> r.idempotencyKey().equals(key)).findFirst();
    }

    @Override
    public List<Refund> findByPaymentId(PaymentId paymentId) {
        return store.values().stream().filter(r -> r.paymentId().equals(paymentId)).toList();
    }

    @Override
    public Optional<Refund> findByGatewayRefundId(String gatewayRefundId) {
        if (gatewayRefundId == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(r -> gatewayRefundId.equals(r.gatewayReference().gatewayRefundId()))
                .findFirst();
    }
}
