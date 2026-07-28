package com.roadscanner.paymentservice.domain.model;

/**
 * A <strong>closed, platform-defined</strong> enum of payment methods (FR-4.1) — closed for the
 * same reason {@code provider-integration-service}'s {@code ProviderCapability} is: this vocabulary
 * is platform-defined and grows only when <em>this service's</em> feature set does, never when a
 * gateway is added. Whether a given gateway supports a given method is the adapter's concern, not
 * the domain's (docs/services/payment-service/domain-model.md).
 */
public enum PaymentMethod {
    CARD,
    UPI,
    WALLET,
    NETBANKING
}
