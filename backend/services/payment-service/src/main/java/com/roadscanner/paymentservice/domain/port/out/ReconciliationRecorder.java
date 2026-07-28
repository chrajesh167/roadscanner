package com.roadscanner.paymentservice.domain.port.out;

import java.time.Instant;

/** Records reconciliation discrepancies for support — never used to silently move money
 * (docs/services/payment-service/use-cases.md's "Reconcile Cancelled Booking" /
 * "Reconcile Against Gateway"). Insert-only. */
public interface ReconciliationRecorder {

    void recordDiscrepancy(String kind, String subjectReference, String detail, Instant detectedAt);
}
