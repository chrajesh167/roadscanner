package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;

/** Publishes an {@link AuditRecord} to Kafka (topic {@code provider-integration-events} — see
 * docs/services/provider-integration-service/events-published.md). The Postgres side of the same
 * event is written separately via {@link AuditRecordRepository}; callers write both, in that
 * order, so a durable record always exists even if nothing is consuming the topic yet. */
public interface AuditPublisher {

    void publish(AuditRecord record);
}
