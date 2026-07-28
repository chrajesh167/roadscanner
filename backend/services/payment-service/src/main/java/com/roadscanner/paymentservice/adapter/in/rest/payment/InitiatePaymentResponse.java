package com.roadscanner.paymentservice.adapter.in.rest.payment;

import com.roadscanner.paymentservice.domain.port.in.InitiatePayment;

import java.util.UUID;

public record InitiatePaymentResponse(UUID paymentId, String status) {

    static InitiatePaymentResponse from(InitiatePayment.Result result) {
        return new InitiatePaymentResponse(result.paymentId().value(), result.status().name());
    }
}
