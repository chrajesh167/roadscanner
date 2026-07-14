package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.UserId;

import java.util.Optional;

/**
 * Persistence port for {@link Credential}. Implemented by a Postgres/JPA adapter in
 * adapter.out.persistence (not built today — see
 * docs/services/auth-service/implementation-roadmap.md step 4).
 */
public interface CredentialRepository {

    Optional<Credential> findByLoginIdentifier(LoginIdentifier loginIdentifier);

    Optional<Credential> findByUserId(UserId userId);

    boolean existsByLoginIdentifier(LoginIdentifier loginIdentifier);

    Credential save(Credential credential);
}
