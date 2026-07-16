package com.roadscanner.authservice.testsupport.fakes;

import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Map-backed CredentialRepository for framework-free use-case tests. */
public final class InMemoryCredentialRepository implements CredentialRepository {

    private final Map<UserId, Credential> byUserId = new ConcurrentHashMap<>();

    @Override
    public Optional<Credential> findByLoginIdentifier(LoginIdentifier loginIdentifier) {
        return byUserId.values().stream()
                .filter(credential -> credential.loginIdentifier().equals(loginIdentifier))
                .findFirst();
    }

    @Override
    public Optional<Credential> findByUserId(UserId userId) {
        return Optional.ofNullable(byUserId.get(userId));
    }

    @Override
    public boolean existsByLoginIdentifier(LoginIdentifier loginIdentifier) {
        return findByLoginIdentifier(loginIdentifier).isPresent();
    }

    @Override
    public Credential save(Credential credential) {
        byUserId.put(credential.userId(), credential);
        return credential;
    }
}
