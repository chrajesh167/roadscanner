package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditPublisher;

import java.util.ArrayList;
import java.util.List;

public final class RecordingAuditPublisher implements AuditPublisher {

    private final List<AuditRecord> published = new ArrayList<>();

    @Override
    public void publish(AuditRecord record) {
        published.add(record);
    }

    public List<AuditRecord> published() {
        return List.copyOf(published);
    }
}
