package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.port.out.PaymentAuditPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class NoOpAuditPort implements PaymentAuditPort {

    public final List<String> records = new ArrayList<>();

    @Override
    public void record(String eventType, String subjectReference, String detail, Instant occurredAt) {
        records.add(eventType + ":" + subjectReference);
    }
}
