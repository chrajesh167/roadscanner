package com.roadscanner.providerintegrationservice.application.usecase.booking;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.GetOrderDetails;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

public class GetOrderDetailsService implements GetOrderDetails {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;

    public GetOrderDetailsService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
    }

    @Override
    public Result get(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(),
                ProviderCapability.ORDER_DETAILS);

        ProviderOrder order = client.getOrderDetails(session, command.providerOrderReference(),
                command.providerOrderToken());
        return new Result(order);
    }
}
