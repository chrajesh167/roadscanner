package com.roadscanner.providerintegrationservice.application.usecase.registry;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import com.roadscanner.providerintegrationservice.domain.port.in.TestProviderConnection;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;

/**
 * Implements {@link TestProviderConnection} by translating a registry id into a
 * {@code ProviderType} and delegating to the existing {@link CheckProviderHealth}.
 *
 * <p>Everything this class does is the lookup. The probe itself, the durable health record, and
 * the {@code ProviderUnavailable}/{@code ProviderRecovered} events on a state transition all stay
 * in the one implementation shared with the scheduler and the internal health endpoint — so an
 * admin-triggered test is genuinely the same check the platform runs, not a lookalike that could
 * report differently.
 */
public class TestProviderConnectionService implements TestProviderConnection {

    private final ProviderConfigurationRepository repository;
    private final CheckProviderHealth checkProviderHealth;

    public TestProviderConnectionService(ProviderConfigurationRepository repository,
                                         CheckProviderHealth checkProviderHealth) {
        this.repository = repository;
        this.checkProviderHealth = checkProviderHealth;
    }

    @Override
    public Result test(Command command) {
        Provider provider = repository.findById(command.providerId())
                .orElseThrow(() -> new ProviderNotFoundException(command.providerId()));

        CheckProviderHealth.Result result =
                checkProviderHealth.check(new CheckProviderHealth.Command(provider.type()));

        return new Result(result.health());
    }
}
