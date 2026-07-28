package com.roadscanner.paymentservice.adapter.in.rest.refund;

import com.roadscanner.paymentservice.domain.model.RefundReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** {@code Initiate Refund} body — called by {@code booking-service} (service-to-service) or
 * admin/support override. {@code amount}/{@code currency} null means a full refund of the captured
 * amount. The amount is computed by {@code booking-service}; this service never computes a policy. */
public record InitiateRefundRequest(
        @Positive BigDecimal amount,
        String currency,
        @NotNull RefundReason reason) {
}
