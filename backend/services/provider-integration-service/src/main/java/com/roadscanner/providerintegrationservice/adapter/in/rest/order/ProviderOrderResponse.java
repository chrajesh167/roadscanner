package com.roadscanner.providerintegrationservice.adapter.in.rest.order;

import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;

import java.util.Map;

/**
 * {@code details} is passed through as the provider returned it. Order payloads differ per provider
 * and this service makes no decisions from them, so imposing a schema would mean inventing a shape
 * none of them share and re-cutting it whenever a provider added a field.
 */
record ProviderOrderResponse(String providerOrderReference, String providerType, Map<String, Object> details) {

    static ProviderOrderResponse from(ProviderOrder order) {
        return new ProviderOrderResponse(order.providerOrderReference(), order.providerType().code(),
                order.details());
    }
}
