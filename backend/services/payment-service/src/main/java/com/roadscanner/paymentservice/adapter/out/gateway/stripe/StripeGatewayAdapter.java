package com.roadscanner.paymentservice.adapter.out.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.paymentservice.adapter.out.gateway.AbstractStubPaymentGateway;
import com.roadscanner.paymentservice.config.PaymentProperties;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import org.springframework.stereotype.Component;

/** Stripe adapter — stub today, the only package that would import the Stripe SDK once integrated. */
@Component
public class StripeGatewayAdapter extends AbstractStubPaymentGateway {

    public static final GatewayType TYPE = new GatewayType("STRIPE");

    public StripeGatewayAdapter(ObjectMapper objectMapper, PaymentProperties properties) {
        super(objectMapper, properties.gateways().webhookSecretFor("STRIPE"));
    }

    @Override
    public GatewayType type() {
        return TYPE;
    }
}
