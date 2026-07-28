package com.roadscanner.paymentservice.adapter.out.gateway.adyen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.paymentservice.adapter.out.gateway.AbstractStubPaymentGateway;
import com.roadscanner.paymentservice.config.PaymentProperties;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import org.springframework.stereotype.Component;

/** Adyen adapter — stub today, the only package that would import the Adyen SDK once integrated. */
@Component
public class AdyenGatewayAdapter extends AbstractStubPaymentGateway {

    public static final GatewayType TYPE = new GatewayType("ADYEN");

    public AdyenGatewayAdapter(ObjectMapper objectMapper, PaymentProperties properties) {
        super(objectMapper, properties.gateways().webhookSecretFor("ADYEN"));
    }

    @Override
    public GatewayType type() {
        return TYPE;
    }
}
