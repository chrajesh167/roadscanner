package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.port.out.ReconciliationRecorder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
class ReconciliationRecorderAdapter implements ReconciliationRecorder {

    private final ReconciliationSpringDataRepository springDataRepository;

    ReconciliationRecorderAdapter(ReconciliationSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void recordDiscrepancy(String kind, String subjectReference, String detail, Instant detectedAt) {
        springDataRepository.save(new ReconciliationRecordJpaEntity(UUID.randomUUID(), kind, subjectReference,
                truncate(detail), detectedAt));
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 1024 ? detail : detail.substring(0, 1024);
    }
}
