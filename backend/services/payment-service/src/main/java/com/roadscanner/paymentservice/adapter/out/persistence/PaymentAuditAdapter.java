package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.port.out.PaymentAuditPort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
class PaymentAuditAdapter implements PaymentAuditPort {

    private final PaymentAuditSpringDataRepository springDataRepository;

    PaymentAuditAdapter(PaymentAuditSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void record(String eventType, String subjectReference, String detail, Instant occurredAt) {
        springDataRepository.save(new PaymentAuditRecordJpaEntity(UUID.randomUUID(), eventType, subjectReference,
                truncate(detail), occurredAt));
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 1024 ? detail : detail.substring(0, 1024);
    }
}
