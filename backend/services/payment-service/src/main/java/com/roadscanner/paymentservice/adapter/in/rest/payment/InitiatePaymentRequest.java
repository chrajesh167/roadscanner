package com.roadscanner.paymentservice.adapter.in.rest.payment;

import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code Initiate Payment} request body. {@code gatewayType} is optional — the configured default
 * is used when absent. The payer's identity comes from the JWT subject, not the body. */
public record InitiatePaymentRequest(
        @NotNull UUID bookingReference,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotNull PaymentMethod method,
        String gatewayType) {
}
