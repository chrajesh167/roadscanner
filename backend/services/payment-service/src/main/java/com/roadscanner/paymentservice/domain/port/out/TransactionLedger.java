package com.roadscanner.paymentservice.domain.port.out;

import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentTransaction;

import java.util.List;

/** Append-only internal ledger of money movements (capture, refund) — the "internal ledger of
 * transactions" docs/architecture/service-boundaries.md assigns to this service. Insert-only; the
 * input a future accounting/settlement service would read (docs/services/payment-service/boundaries.md). */
public interface TransactionLedger {

    void append(PaymentTransaction transaction);

    List<PaymentTransaction> findByPaymentId(PaymentId paymentId);
}
