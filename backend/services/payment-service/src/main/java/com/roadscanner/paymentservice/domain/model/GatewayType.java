package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;

/**
 * An <strong>open value object</strong> (normalized code string), not a closed {@code enum} —
 * copied deliberately from {@code provider-integration-service}'s {@code ProviderType}
 * (docs/services/provider-integration-service/domain-model.md). This is the entire mechanism behind
 * "add a gateway (Razorpay, Stripe, Adyen, ...) without changing business logic": a new gateway is
 * a new adapter package plus a configuration row, resolved at runtime by
 * {@code PaymentGatewayRegistry}, never a new enum constant the domain has to know about
 * (docs/services/payment-service/domain-model.md's "Payment Gateway Abstraction").
 */
public record GatewayType(String code) {

    public GatewayType {
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.trim().toUpperCase();
    }

    @Override
    public String toString() {
        return code;
    }
}
