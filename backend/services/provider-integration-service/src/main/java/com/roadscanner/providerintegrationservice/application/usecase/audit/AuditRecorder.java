package com.roadscanner.providerintegrationservice.application.usecase.audit;

import com.roadscanner.providerintegrationservice.domain.model.AuditEventType;
import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditPublisher;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditRecordRepository;

import java.time.Clock;

/**
 * Shared by every place this service raises {@code ProviderUnavailable}/{@code ProviderRecovered}/
 * {@code SessionExpired} ({@code CheckProviderHealthService}, {@code SessionExpirySweeper}) — the
 * "write the durable record, then publish to Kafka" sequencing
 * ({@link com.roadscanner.providerintegrationservice.domain.port.out.AuditPublisher}'s Javadoc)
 * lives in exactly one place so it can't drift between callers.
 */
public class AuditRecorder {

    private final AuditRecordRepository auditRecordRepository;
    private final AuditPublisher auditPublisher;
    private final Clock clock;

    public AuditRecorder(AuditRecordRepository auditRecordRepository, AuditPublisher auditPublisher, Clock clock) {
        this.auditRecordRepository = auditRecordRepository;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    public void record(ProviderType providerType, AuditEventType eventType, ProviderSessionId sessionId, String message) {
        AuditRecord record = AuditRecord.of(providerType, eventType, sessionId, message, clock.instant());
        auditRecordRepository.save(record);
        auditPublisher.publish(record);
    }
}
