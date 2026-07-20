package com.roadscanner.providerintegrationservice.application.usecase.health;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;

/** Drives {@link CheckProviderHealth} for every enabled provider — the orchestration behind
 * {@code config}'s {@code @Scheduled} polling bean. A single provider's probe failing (e.g. an
 * unexpected exception the resolved adapter didn't itself catch) does not stop the sweep for the
 * remaining providers. */
public class ProviderHealthMonitor {

    private final ProviderConfigurationRepository configurationRepository;
    private final CheckProviderHealth checkProviderHealth;

    public ProviderHealthMonitor(ProviderConfigurationRepository configurationRepository,
                                  CheckProviderHealth checkProviderHealth) {
        this.configurationRepository = configurationRepository;
        this.checkProviderHealth = checkProviderHealth;
    }

    public void checkAllEnabledProviders() {
        for (Provider provider : configurationRepository.findAllEnabled()) {
            checkProviderHealth.check(new CheckProviderHealth.Command(provider.type()));
        }
    }
}
