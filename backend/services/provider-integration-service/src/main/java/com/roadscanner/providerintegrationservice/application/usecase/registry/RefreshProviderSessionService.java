package com.roadscanner.providerintegrationservice.application.usecase.registry;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;

/**
 * Implements {@link RefreshProviderSession} by translating a registry id into a
 * {@code ProviderType} and delegating to the existing {@link AuthenticateProvider}.
 *
 * <p>Authenticating afresh, rather than exchanging an existing session's token, is the right
 * behaviour for the operation the admin console is actually invoking: it is used after rotating
 * credentials, when the goal is to prove the <em>new</em> secrets work. Exchanging the old
 * session's token would succeed on the strength of the old credentials and prove nothing.
 *
 * <p>All credential handling and session persistence stay in {@code AuthenticateProvider}'s
 * implementation — this class adds only the lookup.
 */
public class RefreshProviderSessionService implements RefreshProviderSession {

    private final ProviderConfigurationRepository repository;
    private final AuthenticateProvider authenticateProvider;

    public RefreshProviderSessionService(ProviderConfigurationRepository repository,
                                         AuthenticateProvider authenticateProvider) {
        this.repository = repository;
        this.authenticateProvider = authenticateProvider;
    }

    @Override
    public Result refresh(Command command) {
        Provider provider = repository.findById(command.providerId())
                .orElseThrow(() -> new ProviderNotFoundException(command.providerId()));

        AuthenticateProvider.Result result =
                authenticateProvider.authenticate(new AuthenticateProvider.Command(provider.type()));

        return new Result(result.sessionId(), result.expiresAt());
    }
}
