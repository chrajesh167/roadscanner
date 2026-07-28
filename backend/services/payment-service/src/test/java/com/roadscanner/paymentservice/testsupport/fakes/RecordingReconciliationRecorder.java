package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.port.out.ReconciliationRecorder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RecordingReconciliationRecorder implements ReconciliationRecorder {

    public final List<String> discrepancies = new ArrayList<>();

    @Override
    public void recordDiscrepancy(String kind, String subjectReference, String detail, Instant detectedAt) {
        discrepancies.add(kind + ":" + subjectReference);
    }
}
