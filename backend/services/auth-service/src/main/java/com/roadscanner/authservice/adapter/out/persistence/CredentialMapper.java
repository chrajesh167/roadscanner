package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.AccountStatus;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.PasswordHash;
import com.roadscanner.authservice.domain.model.UserId;

/**
 * The one class in this package permitted to import from {@code domain.model} — everything
 * else in {@code adapter.out.persistence} either only knows the domain (the repository ports,
 * elsewhere) or only knows JPA (the entities, the Spring Data interfaces). Stateless by design;
 * owned as a plain field by {@link CredentialRepositoryAdapter} rather than injected, since it
 * has no dependencies of its own to substitute in tests.
 */
final class CredentialMapper {

    Credential toDomain(CredentialJpaEntity entity) {
        return Credential.reconstitute(
                new UserId(entity.getId()),
                new LoginIdentifier(entity.getLoginIdentifier()),
                new PasswordHash(entity.getPasswordHash(), entity.getPasswordAlgorithmId()),
                AccountStatus.valueOf(entity.getStatus()),
                entity.getFailedLoginAttempts(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastLoginAt()
        );
    }

    CredentialJpaEntity toNewEntity(Credential credential) {
        return new CredentialJpaEntity(
                credential.userId().value(),
                credential.loginIdentifier().value(),
                credential.passwordHash().value(),
                credential.passwordHash().algorithmId(),
                credential.status().name(),
                credential.failedLoginAttempts(),
                credential.createdAt(),
                credential.updatedAt(),
                credential.lastLoginAt().orElse(null)
        );
    }

    /** Updates an already-managed entity in place — see {@link CredentialRepositoryAdapter}
     * for why this is distinct from {@link #toNewEntity}. */
    void applyTo(CredentialJpaEntity entity, Credential credential) {
        entity.applyMutableState(
                credential.passwordHash().value(),
                credential.passwordHash().algorithmId(),
                credential.status().name(),
                credential.failedLoginAttempts(),
                credential.updatedAt(),
                credential.lastLoginAt().orElse(null)
        );
    }
}
