package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implements the {@link RefreshTokenRepository} domain port over Postgres via JPA. See
 * {@link CredentialRepositoryAdapter}'s Javadoc for why {@link #save} fetches-then-mutates
 * rather than always constructing a fresh entity — the same optimistic-locking reasoning
 * applies here.
 */
@Repository
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenSpringDataRepository springDataRepository;
    private final RefreshTokenMapper mapper = new RefreshTokenMapper();

    RefreshTokenRepositoryAdapter(RefreshTokenSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(TokenHash tokenHash) {
        return springDataRepository.findByTokenHash(tokenHash.value()).map(mapper::toDomain);
    }

    @Override
    public List<RefreshToken> findByUserId(UserId userId) {
        return springDataRepository.findByUserId(userId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = springDataRepository.findById(refreshToken.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, refreshToken);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(refreshToken));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
