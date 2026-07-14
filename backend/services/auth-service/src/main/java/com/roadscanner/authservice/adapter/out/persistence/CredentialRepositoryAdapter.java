package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Implements the {@link CredentialRepository} domain port over Postgres via JPA. Package-private
 * — consumers depend on the {@link CredentialRepository} interface only, never this concrete
 * type, so the implementation detail (that it's JPA-backed at all) stays fully swappable.
 *
 * {@link #save} deliberately does not just construct a fresh {@link CredentialJpaEntity} and
 * call {@code save()} on it unconditionally. For an update, that would hand Hibernate an entity
 * with no {@code @Version} value read from the database, silently bypassing the optimistic-lock
 * check that {@code @Version} exists to provide. Instead: fetch the existing managed entity (if
 * any), mutate it in place (which preserves the version Hibernate already read), and only
 * construct a brand-new entity for a genuine insert.
 */
@Repository
class CredentialRepositoryAdapter implements CredentialRepository {

    private final CredentialSpringDataRepository springDataRepository;
    private final CredentialMapper mapper = new CredentialMapper();

    CredentialRepositoryAdapter(CredentialSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Credential> findByLoginIdentifier(LoginIdentifier loginIdentifier) {
        return springDataRepository.findByLoginIdentifier(loginIdentifier.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Credential> findByUserId(UserId userId) {
        return springDataRepository.findById(userId.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByLoginIdentifier(LoginIdentifier loginIdentifier) {
        return springDataRepository.existsByLoginIdentifier(loginIdentifier.value());
    }

    @Override
    public Credential save(Credential credential) {
        CredentialJpaEntity entity = springDataRepository.findById(credential.userId().value())
                .map(existing -> {
                    mapper.applyTo(existing, credential);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(credential));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
