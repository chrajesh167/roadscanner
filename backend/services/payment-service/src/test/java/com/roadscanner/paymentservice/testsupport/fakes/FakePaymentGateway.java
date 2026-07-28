package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.GatewayWebhookEvent;
import com.roadscanner.paymentservice.domain.model.WebhookEventType;
import com.roadscanner.paymentservice.domain.port.out.PaymentGateway;
import com.roadscanner.paymentservice.domain.port.out.PaymentGatewayRegistry;

import java.util.Set;

/** A deterministic in-memory gateway + registry for application-layer tests. */
public class FakePaymentGateway implements PaymentGateway, PaymentGatewayRegistry {

    public static final GatewayType TYPE = new GatewayType("FAKE");

    @Override
    public GatewayType type() {
        return TYPE;
    }

    @Override
    public GatewayReference initiateCharge(ChargeRequest request) {
        return GatewayReference.ofPayment("fake-order-" + request.paymentId(), "fake-pay-" + request.paymentId());
    }

    @Override
    public GatewayReference refund(RefundRequest request) {
        return GatewayReference.ofRefund("fake-refund-" + request.refundId());
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        return true;
    }

    @Override
    public GatewayWebhookEvent parseWebhook(String rawPayload) {
        return new GatewayWebhookEvent(TYPE, "evt-" + rawPayload.hashCode(), WebhookEventType.PAYMENT_CAPTURED,
                rawPayload, null, null, null);
    }

    @Override
    public PaymentGateway resolve(GatewayType type) {
        return this;
    }

    @Override
    public Set<GatewayType> supportedGatewayTypes() {
        return Set.of(TYPE);
    }
}
