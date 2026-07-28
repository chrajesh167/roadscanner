package com.roadscanner.paymentservice.adapter.in.rest.payment;

import com.roadscanner.paymentservice.domain.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Full payment view — never includes any instrument data (NFR-12), only references and status. */
public record PaymentResponse(UUID paymentId, UUID bookingReference, BigDecimal amount, String currency,
                              String method, String gatewayType, String status, int attempts, Instant createdAt) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.id().value(), payment.bookingReference().value(),
                payment.amount().amount(), payment.amount().currency().getCurrencyCode(), payment.method().name(),
                payment.gatewayType().code(), payment.status().name(), payment.attempts().size(), payment.createdAt());
    }
}
