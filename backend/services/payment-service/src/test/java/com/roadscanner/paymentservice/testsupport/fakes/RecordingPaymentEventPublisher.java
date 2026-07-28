package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.Refund;
import com.roadscanner.paymentservice.domain.port.out.PaymentEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RecordingPaymentEventPublisher implements PaymentEventPublisher {

    public final List<String> published = new ArrayList<>();

    @Override
    public void publishPaymentCreated(Payment payment, Instant occurredAt) {
        published.add("PaymentCreated:" + payment.id());
    }

    @Override
    public void publishPaymentCompleted(Payment payment, Instant occurredAt) {
        published.add("PaymentCompleted:" + payment.id());
    }

    @Override
    public void publishPaymentFailed(Payment payment, Instant occurredAt) {
        published.add("PaymentFailed:" + payment.id());
    }

    @Override
    public void publishPaymentTimedOut(Payment payment, Instant occurredAt) {
        published.add("PaymentTimedOut:" + payment.id());
    }

    @Override
    public void publishRefundInitiated(Refund refund, Instant occurredAt) {
        published.add("RefundInitiated:" + refund.id());
    }

    @Override
    public void publishRefundCompleted(Refund refund, Instant occurredAt) {
        published.add("RefundCompleted:" + refund.id());
    }

    @Override
    public void publishRefundFailed(Refund refund, Instant occurredAt) {
        published.add("RefundFailed:" + refund.id());
    }
}
