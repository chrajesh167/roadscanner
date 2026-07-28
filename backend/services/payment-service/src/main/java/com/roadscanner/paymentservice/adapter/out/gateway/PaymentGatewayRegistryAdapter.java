package com.roadscanner.paymentservice.adapter.out.gateway;

import com.roadscanner.paymentservice.domain.exception.UnsupportedGatewayException;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.port.out.PaymentGateway;
import com.roadscanner.paymentservice.domain.port.out.PaymentGatewayRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves a {@link GatewayType} to its adapter at runtime — the direct analogue of
 * {@code provider-integration-service}'s {@code ProviderClientRegistry}. Collects every
 * {@link PaymentGateway} bean Spring discovers, so adding a gateway is a new adapter bean plus a
 * configuration row, never a change here (docs/services/payment-service/domain-model.md's "Payment
 * Gateway Abstraction").
 */
@Component
public class PaymentGatewayRegistryAdapter implements PaymentGatewayRegistry {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayRegistryAdapter.class);

    private final Map<GatewayType, PaymentGateway> gateways;

    public PaymentGatewayRegistryAdapter(List<PaymentGateway> gatewayBeans) {
        this.gateways = gatewayBeans.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentGateway::type, Function.identity()));
        log.info("Registered {} payment gateway adapter(s): {}", gateways.size(), gateways.keySet());
    }

    @Override
    public PaymentGateway resolve(GatewayType type) {
        PaymentGateway gateway = gateways.get(type);
        if (gateway == null) {
            throw new UnsupportedGatewayException(type.code());
        }
        return gateway;
    }

    @Override
    public Set<GatewayType> supportedGatewayTypes() {
        return gateways.keySet();
    }
}
