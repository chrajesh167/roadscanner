package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditRecordRepository;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryAuditRecordRepository implements AuditRecordRepository {

    private final List<AuditRecord> records = new ArrayList<>();

    @Override
    public AuditRecord save(AuditRecord record) {
        records.add(record);
        return record;
    }

    public List<AuditRecord> all() {
        return List.copyOf(records);
    }
}
