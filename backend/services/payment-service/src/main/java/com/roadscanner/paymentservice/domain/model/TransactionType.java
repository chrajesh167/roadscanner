package com.roadscanner.paymentservice.domain.model;

/** The kind of money movement a {@link PaymentTransaction} ledger line records. */
public enum TransactionType {
    CAPTURE,
    REFUND
}
