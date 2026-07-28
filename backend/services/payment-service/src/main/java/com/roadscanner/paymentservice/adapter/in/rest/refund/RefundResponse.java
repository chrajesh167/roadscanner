package com.roadscanner.paymentservice.adapter.in.rest.refund;

import com.roadscanner.paymentservice.domain.model.Refund;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundResponse(UUID refundId, UUID paymentId, BigDecimal amount, String currency, boolean fullRefund,
                             String reason, String status) {

    static RefundResponse from(Refund refund) {
        return new RefundResponse(refund.id().value(), refund.paymentId().value(), refund.amount().amount(),
                refund.amount().currency().getCurrencyCode(), refund.isFullRefund(), refund.reason().name(),
                refund.status().name());
    }
}
