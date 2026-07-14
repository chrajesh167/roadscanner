package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.port.out.PasswordResetRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Implements the {@link PasswordResetRepository} domain port over Postgres via JPA. See
 * {@link CredentialRepositoryAdapter}'s Javadoc for why {@link #save} fetches-then-mutates
 * rather than always constructing a fresh entity — the same optimistic-locking reasoning
 * applies here, and matters more than usual for this aggregate: {@code use()}'s single-use
 * guarantee is only actually enforced under concurrent requests because of this version check.
 */
@Repository
class PasswordResetRequestRepositoryAdapter implements PasswordResetRepository {

    private final PasswordResetRequestSpringDataRepository springDataRepository;
    private final PasswordResetRequestMapper mapper = new PasswordResetRequestMapper();

    PasswordResetRequestRepositoryAdapter(PasswordResetRequestSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<PasswordResetRequest> findByTokenHash(TokenHash tokenHash) {
        return springDataRepository.findByTokenHash(tokenHash.value()).map(mapper::toDomain);
    }

    @Override
    public PasswordResetRequest save(PasswordResetRequest request) {
        PasswordResetRequestJpaEntity entity = springDataRepository.findById(request.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, request);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(request));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
