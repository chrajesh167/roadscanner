package com.roadscanner.paymentservice.adapter.in.rest.payment;

import com.roadscanner.paymentservice.domain.port.in.GetPaymentStatus;

import java.util.UUID;

public record PaymentStatusResponse(UUID paymentId, String status) {

    static PaymentStatusResponse from(GetPaymentStatus.Result result) {
        return new PaymentStatusResponse(result.paymentId().value(), result.status().name());
    }
}
