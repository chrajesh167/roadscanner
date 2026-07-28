package com.roadscanner.paymentservice.domain.port.out;

import com.roadscanner.paymentservice.domain.model.GatewayType;

import java.util.Set;

/**
 * Resolves a {@link GatewayType} to the concrete {@link PaymentGateway} adapter at runtime — the
 * direct analogue of {@code provider-integration-service}'s {@code ProviderClientRegistry}, and
 * "the entire mechanism behind adding a gateway without changing business logic"
 * (docs/services/payment-service/domain-model.md's "Payment Gateway Abstraction"). The application
 * layer depends on this port, never on a concrete adapter.
 */
public interface PaymentGatewayRegistry {

    /** @throws com.roadscanner.paymentservice.domain.exception.UnsupportedGatewayException if no
     * adapter is registered for the type. */
    PaymentGateway resolve(GatewayType type);

    Set<GatewayType> supportedGatewayTypes();
}
