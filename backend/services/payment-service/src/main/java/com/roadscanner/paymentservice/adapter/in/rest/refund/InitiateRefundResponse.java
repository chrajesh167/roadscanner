package com.roadscanner.paymentservice.adapter.in.rest.refund;

import com.roadscanner.paymentservice.domain.port.in.InitiateRefund;

import java.util.UUID;

public record InitiateRefundResponse(UUID refundId, String status) {

    static InitiateRefundResponse from(InitiateRefund.Result result) {
        return new InitiateRefundResponse(result.refundId().value(), result.status().name());
    }
}
