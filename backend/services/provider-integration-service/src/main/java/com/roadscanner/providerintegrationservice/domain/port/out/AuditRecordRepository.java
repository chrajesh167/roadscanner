package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;

/** Durable persistence port for {@link AuditRecord} — the Postgres side of every audit event
 * this service raises. */
public interface AuditRecordRepository {

    AuditRecord save(AuditRecord record);
}
