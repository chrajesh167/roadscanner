package com.roadscanner.providerintegrationservice.application.usecase.capability;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.port.in.GetProviderCapabilities;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCache;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.util.Set;
import java.util.stream.Collectors;

/** Implements {@link GetProviderCapabilities} — see that port's Javadoc for why the result is
 * the intersection of what's configured and what the adapter itself implements. */
public class GetProviderCapabilitiesService implements GetProviderCapabilities {

    private final ProviderConfigurationRepository configurationRepository;
    private final ProviderClientRegistry registry;
    private final ProviderCache providerCache;

    public GetProviderCapabilitiesService(ProviderConfigurationRepository configurationRepository,
                                           ProviderClientRegistry registry, ProviderCache providerCache) {
        this.configurationRepository = configurationRepository;
        this.registry = registry;
        this.providerCache = providerCache;
    }

    @Override
    public Result getCapabilities(Command command) {
        Provider provider = configurationRepository.findByType(command.providerType())
                .orElseThrow(() -> new ProviderNotSupportedException(command.providerType()));
        ProviderClient client = registry.resolve(command.providerType());

        Set<ProviderCapability> capabilities = providerCache.getCapabilities(command.providerType())
                .orElseGet(() -> resolveAndCache(provider, client));

        return new Result(command.providerType(), capabilities);
    }

    private Set<ProviderCapability> resolveAndCache(Provider provider, ProviderClient client) {
        Set<ProviderCapability> capabilities = provider.capabilities().stream()
                .filter(client.supportedCapabilities()::contains)
                .collect(Collectors.toUnmodifiableSet());
        providerCache.putCapabilities(provider.type(), capabilities);
        return capabilities;
    }
}
