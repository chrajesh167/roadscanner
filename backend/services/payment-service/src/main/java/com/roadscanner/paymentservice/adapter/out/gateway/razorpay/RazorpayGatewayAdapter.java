package com.roadscanner.paymentservice.adapter.out.gateway.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.paymentservice.adapter.out.gateway.AbstractStubPaymentGateway;
import com.roadscanner.paymentservice.config.PaymentProperties;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import org.springframework.stereotype.Component;

/**
 * Razorpay adapter — the only package in the platform that would import the Razorpay SDK once it is
 * integrated. Today a deterministic stub extending {@link AbstractStubPaymentGateway}
 * (docs/services/payment-service/domain-model.md's "Payment Gateway Abstraction"). Registered by
 * {@code PaymentGatewayRegistryAdapter} under {@link #type()}.
 */
@Component
public class RazorpayGatewayAdapter extends AbstractStubPaymentGateway {

    public static final GatewayType TYPE = new GatewayType("RAZORPAY");

    public RazorpayGatewayAdapter(ObjectMapper objectMapper, PaymentProperties properties) {
        super(objectMapper, properties.gateways().webhookSecretFor("RAZORPAY"));
    }

    @Override
    public GatewayType type() {
        return TYPE;
    }
}
