package com.roadscanner.paymentservice.domain.model;

/**
 * A coarse, gateway-agnostic reason echoed from {@code booking-service}'s cancellation — recorded
 * for audit and reconciliation. {@code payment-service} never decides <em>whether</em> a refund is
 * owed; it records the reason {@code booking-service} supplies alongside the amount
 * (docs/services/payment-service/responsibilities.md's non-responsibilities).
 */
public enum RefundReason {
    TRAVELER_REQUESTED,
    TRIP_CANCELLED,
    PROVIDER_CONFIRMATION_FAILED,
    LATE_SUCCESS_AFTER_TIMEOUT,
    SUPPORT_OVERRIDE
}
